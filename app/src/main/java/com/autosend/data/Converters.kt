package com.autosend.data

import androidx.room.TypeConverter

/** Stores enums as their name strings so the schema is stable and readable. */
class Converters {
    @TypeConverter fun targetToString(v: TargetApp): String = v.name
    @TypeConverter fun stringToTarget(v: String): TargetApp = TargetApp.valueOf(v)

    @TypeConverter fun statusToString(v: SendStatus): String = v.name
    @TypeConverter fun stringToStatus(v: String): SendStatus = SendStatus.valueOf(v)

    @TypeConverter fun kindToString(v: AttachmentKind): String = v.name
    @TypeConverter fun stringToKind(v: String): AttachmentKind = AttachmentKind.valueOf(v)
}
