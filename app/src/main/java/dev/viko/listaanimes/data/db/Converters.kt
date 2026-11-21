package dev.viko.listaanimes.data.db

import androidx.room.TypeConverter
import dev.viko.listaanimes.domain.ListStatus
import dev.viko.listaanimes.domain.MediaKind
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class Converters {
    private val listSer = ListSerializer(String.serializer())

    @TypeConverter fun fromList(v: List<String>): String = Json.encodeToString(listSer, v)
    @TypeConverter fun toList(v: String): List<String> = runCatching { Json.decodeFromString(listSer, v) }.getOrDefault(emptyList())
    @TypeConverter fun fromKind(v: MediaKind): String = v.name
    @TypeConverter fun toKind(v: String): MediaKind = MediaKind.valueOf(v)
    @TypeConverter fun fromStatus(v: ListStatus): String = v.name
    @TypeConverter fun toStatus(v: String): ListStatus = ListStatus.valueOf(v)
    @TypeConverter fun fromChange(v: ChangeType): String = v.name
    @TypeConverter fun toChange(v: String): ChangeType = ChangeType.valueOf(v)
}
