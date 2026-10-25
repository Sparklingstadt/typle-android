package io.github.sparklingstadt.typle.core

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class SharedContractTest {
  // JSON numbers 5 and 5.0 have the same saved meaning (the JVM encoder writes a Double with .0).
  private fun numericValues(value: JsonElement): JsonElement = when (value) {
    is JsonObject -> JsonObject(value.mapValues { numericValues(it.value) })
    is JsonArray -> JsonArray(value.map(::numericValues))
    is JsonPrimitive -> if (value.isString) value else value.doubleOrNull?.let { JsonPrimitive(it) } ?: value
  }
  @Test fun webWorkspaceSurvivesAnAndroidEditAndLocalFileRoundTrip() {
    val root = System.getenv("TYPLE_R")
    assumeTrue("set TYPLE_R to run the shared contract", !root.isNullOrEmpty())
    val fixture = Json.parseToJsonElement(File(root!!, "tests/contracts/workspace.json").readText()).jsonObject
    assertEquals(1, fixture.getValue("version").jsonPrimitive.int)
    val limits = fixture.getValue("limits").jsonObject
    for ((name, value) in mapOf("lists" to MAX_LISTS, "category" to MAX_CATEGORY, "tag" to MAX_TAG, "tags" to MAX_TAGS)) {
      assertEquals(name, value, limits.getValue(name).jsonPrimitive.int)
    }
    val saved = fixture.getValue("saved")
    val workspace = decodeAccountWorkspace(saved.toString().encodeToByteArray())
    val draft = workspace.document.lists.single().copy(name = "Android の編集", records = emptyList(), extra = emptyMap())
    val changed = workspace.copy(document = workspace.document.copy(
      lists = saveList(workspace.document.lists, draft, editing = true),
    ))
    val written = Json.parseToJsonElement(encodeAccountWorkspace(changed).decodeToString()).jsonObject
    val expected = saved.jsonObject.getValue("lists").toString().replace("互換 <&> 😀", "Android の編集")
    assertEquals(numericValues(Json.parseToJsonElement(expected)), numericValues(written.getValue("lists")))
    assertEquals(saved.jsonObject.getValue("devices"), written.getValue("devices"))
    assertEquals(saved.jsonObject.getValue("revision"), written.getValue("revision"))
    val local = WorkspaceFile("guest:local", 1, changed.document)
    assertEquals(changed.document, decodeWorkspaceFile(encodeWorkspaceFile(local), "guest:local").document)
  }
}
