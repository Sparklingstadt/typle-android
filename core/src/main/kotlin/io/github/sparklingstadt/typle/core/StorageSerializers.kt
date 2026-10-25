package io.github.sparklingstadt.typle.core

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

// The server and web app preserve fields introduced by another client. Keep them on the model so copy()/reordering
// retains them too. Fields removed from a model must be explicitly migrated, rather than silently dropped on save.
@OptIn(ExperimentalSerializationApi::class)
private class RetainingSerializer<T, W>(
  private val wire: KSerializer<W>,
  private val read: (W, Map<String, JsonElement>) -> T,
  private val write: (T) -> Pair<W, Map<String, JsonElement>>,
  private val dropped: Set<String> = emptySet(),
) : KSerializer<T> {
  override val descriptor = wire.descriptor
  private val known = (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }.toSet()

  override fun deserialize(decoder: Decoder): T {
    val json = decoder as JsonDecoder
    val fields = json.decodeJsonElement().jsonObject
    return read(json.json.decodeFromJsonElement(wire, fields), fields.filterKeys { it !in known && it !in dropped })
  }

  override fun serialize(encoder: Encoder, value: T) {
    val json = encoder as JsonEncoder
    val (fields, extra) = write(value)
    // Known fields always win, including optional fields deliberately cleared by the user.
    json.encodeJsonElement(JsonObject(extra.filterKeys { it !in known && it !in dropped } +
      json.json.encodeToJsonElement(wire, fields).jsonObject))
  }
}

@Serializable
private data class WordWire(val display: String, val input: String, val annotation: String = "")

object WordStorageSerializer : KSerializer<Word> by RetainingSerializer(
  WordWire.serializer(),
  { value, extra -> Word(value.display, value.input, value.annotation, extra) },
  { value: Word -> WordWire(value.display, value.input, value.annotation) to value.extra },
)

@Serializable
private data class DeviceWire(val id: String, val name: String, val layout: String = "")

object DeviceStorageSerializer : KSerializer<PracticeDevice> by RetainingSerializer(
  DeviceWire.serializer(),
  { value, extra -> PracticeDevice(value.id, value.name, value.layout, extra) },
  { value: PracticeDevice -> DeviceWire(value.id, value.name, value.layout) to value.extra },
)

@Serializable
private data class RecordWire(
  val id: String? = null, val time: Double, val date: String, val mistakes: Int? = null,
  val accuracy: Double? = null, val device: PracticeDevice? = null, val wordCount: Int? = null,
)

object RecordStorageSerializer : KSerializer<StoredRecord> by RetainingSerializer(
  RecordWire.serializer(),
  { value, extra -> StoredRecord(value.id, value.time, value.date, value.mistakes, value.accuracy, value.device, value.wordCount, extra) },
  { value: StoredRecord -> RecordWire(value.id, value.time, value.date, value.mistakes, value.accuracy, value.device, value.wordCount) to value.extra },
)

@Serializable
private data class ListWire(
  val id: String, val name: String, val words: List<Word>, val records: List<StoredRecord> = emptyList(), val createdAt: String,
  val category: String? = null, val tags: List<String>? = null, val group: String? = null, val levels: List<String>? = null,
)

object ListStorageSerializer : KSerializer<WordList> by RetainingSerializer(
  ListWire.serializer(),
  { value, extra -> WordList(value.id, value.name, value.words, value.records, value.createdAt, value.category, value.tags, value.group, value.levels, extra) },
  { value: WordList -> ListWire(value.id, value.name, value.words, value.records, value.createdAt, value.category, value.tags, value.group, value.levels) to value.extra },
  dropped = setOf("shared", "shareId"),
)
