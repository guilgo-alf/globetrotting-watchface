package com.guil.globetrotting.tile

import java.time.ZoneId
import java.util.Locale

/**
 * Saved timezone configuration. PVT in the original Facer face was non-standard;
 * defaulting PVT slot to PDT (US Pacific) per spec §3 recommendation.
 */
data class SavedZone(
    val zoneId: ZoneId,
    val abbreviation: String,
    val flagEmoji: String,
    val cityOverride: String? = null,
)

object TimezoneTileConfig {

    val SAVED_ZONES: List<SavedZone> = listOf(
        SavedZone(ZoneId.of("America/Los_Angeles"), "PDT",  "🇺🇸"), // 🇺🇸
        SavedZone(ZoneId.of("America/New_York"),    "EDT",  "🇺🇸"),
        SavedZone(ZoneId.of("America/Sao_Paulo"),   "BRT",  "🇧🇷"), // 🇧🇷
        SavedZone(ZoneId.of("Europe/Amsterdam"),    "CEST", "🇳🇱", cityOverride = "MAASTRICHT"), // 🇳🇱
        SavedZone(ZoneId.of("Asia/Kolkata"),        "IST",  "🇮🇳"), // 🇮🇳
        SavedZone(ZoneId.of("Asia/Shanghai"),       "CST",  "🇨🇳"), // 🇨🇳
    )

    /** Best-effort city name from a ZoneId — last path segment, prettified. */
    fun cityFromZone(zoneId: ZoneId): String =
        zoneId.id.substringAfterLast('/')
            .replace('_', ' ')
            .uppercase(Locale.ENGLISH)

    /**
     * Best-effort flag from a country-coded ZoneId. Hardcoded for the common travel zones —
     * extending the saved-zones flag set so the travel row always has a flag.
     */
    fun flagFromZone(zoneId: ZoneId): String = when (zoneId.id) {
        "Asia/Tokyo" -> "🇯🇵"
        "Asia/Hong_Kong" -> "🇭🇰"
        "Asia/Singapore" -> "🇸🇬"
        "Asia/Seoul" -> "🇰🇷"
        "Asia/Bangkok" -> "🇹🇭"
        "Asia/Dubai" -> "🇦🇪"
        "America/Mexico_City" -> "🇲🇽"
        "America/Chicago", "America/Denver", "America/Phoenix",
        "America/Anchorage", "America/Honolulu" -> "🇺🇸"
        "America/Toronto", "America/Vancouver" -> "🇨🇦"
        "America/Argentina/Buenos_Aires" -> "🇦🇷"
        "Europe/London" -> "🇬🇧"
        "Europe/Berlin" -> "🇩🇪"
        "Europe/Paris" -> "🇫🇷"
        "Europe/Madrid" -> "🇪🇸"
        "Europe/Rome" -> "🇮🇹"
        "Europe/Vienna" -> "🇦🇹"
        "Europe/Prague" -> "🇨🇿"
        "Europe/Warsaw" -> "🇵🇱"
        "Europe/Stockholm" -> "🇸🇪"
        "Europe/Lisbon" -> "🇵🇹"
        "Europe/Athens" -> "🇬🇷"
        "Europe/Helsinki" -> "🇫🇮"
        "Europe/Moscow" -> "🇷🇺"
        "Australia/Sydney", "Australia/Perth" -> "🇦🇺"
        "Pacific/Auckland" -> "🇳🇿"
        else -> "🌐"
    }
}
