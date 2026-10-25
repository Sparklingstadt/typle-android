package io.github.sparklingstadt.typle.core

import java.io.File
import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

// Actual HTTP through the production Android client, with the same test database and Go/Kotlin commands as the Node
// contract suite. No Android SDK, emulator, external GitHub login or production credential is needed.
class BackendContractTest {
  @Test fun androidLogsInAndSavesAgainstEachBackendWithoutLosingWebData() {
    val raw = System.getenv("TYPLE_COMPAT_TARGETS")
    assumeTrue("run typle-r's test:compat:matrix -- --android for real backend checks", !raw.isNullOrEmpty())
    val fixture = Json.parseToJsonElement(File(System.getenv("TYPLE_R"), "tests/contracts/workspace.json").readText())
      .jsonObject.getValue("saved").jsonObject
    val targets = Json.parseToJsonElement(raw!!).jsonArray
    for (entry in targets) {
      val target = entry.jsonObject
      fun text(key: String) = target.getValue(key).jsonPrimitive.content
      val name = text("name")
      val accountId = text("accountId")
      val login = AccountLogin(URI(text("url")), text("verifier"))
      assertEquals("android", login.signinUrl.query.split('&').first().substringAfter('='))
      val (token, user) = login.finish("typle-r://login?code=${text("code")}")
      assertEquals(accountId, user.id)
      val client = AccountClient(URI(text("url")))
      fun request(method: String, body: JsonObject? = null): JsonObject = Json.parseToJsonElement(
        client.send(method, "api/workspace", body?.toString()?.encodeToByteArray(), token).decodeToString(),
      ).jsonObject
      val before = request("GET")
      val revision = before.getValue("revision").jsonPrimitive.int
      if (revision == 0) {
        request("PUT", JsonObject(fixture + mapOf("accountId" to JsonPrimitive(accountId), "revision" to JsonPrimitive(0))))
      }
      val renewals = mutableListOf<String>()
      val sync = AccountSync(URI(text("url")), text("oldToken"), accountId) { renewals.add(it) }
      val loaded = sync.load()
      assertEquals(1, renewals.size)
      assertTrue(renewals.single().isNotEmpty())
      assertEquals("語学", loaded.lists.single().category)
      assertEquals(listOf("Kotlin", "日本語"), loaded.lists.single().tags)
      assertEquals("kept", loaded.lists.single().records.last().extra.getValue("futureRecord").jsonPrimitive.content)

      // A browser/other device saves after Android loaded. Android must see the 409, reload, and apply the change
      // again without dropping that save or any fields added by another client.
      val elsewhere = request("GET")
      val foreign = PracticeDevice("external-$name", "外部の保存", "US")
      val decoded = decodeAccountWorkspace(elsewhere.toString().encodeToByteArray())
      request("PUT", Json.parseToJsonElement(encodeAccountWorkspace(decoded.copy(document = decoded.document.copy(
        devices = decoded.document.devices + foreign,
      ))).decodeToString()).jsonObject)
      var attempts = 0
      val kept = sync.change { document ->
        attempts++
        document.copy(lists = document.lists.map { it.copy(name = "Android + $name") })
      }
      assertEquals(2, attempts)
      assertTrue(kept.devices.contains(foreign))
      assertEquals("Android + $name", kept.lists.single().name)
      assertNotNull(kept.lists.single().extra["futureList"])
      assertNotNull(kept.lists.single().words.single().extra["futureWord"])
      assertNotNull(kept.lists.single().records.last().device?.extra?.get("futureDevice"))
      val reloaded = decodeAccountWorkspace(request("GET").toString().encodeToByteArray())
      assertEquals(kept, reloaded.document)
      assertEquals(decoded.revision + 2, reloaded.revision)
    }
  }
}
