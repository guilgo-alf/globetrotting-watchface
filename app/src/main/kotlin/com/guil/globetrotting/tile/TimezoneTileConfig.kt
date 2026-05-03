package com.guil.globetrotting.tile

import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Format an offset like `+ 5:30`, `− 7`. Empty string when delta is zero
 * (used for the highlighted local row). Single source of truth shared by both
 * the tile (ExtrasTileRenderer) and the activity (ZonesActivity).
 */
fun formatOffset(deltaSeconds: Int): String {
    if (deltaSeconds == 0) return ""
    val sign = if (deltaSeconds < 0) "−" else "+"
    val absHours = Math.abs(deltaSeconds) / 3600
    val absMins = (Math.abs(deltaSeconds) % 3600) / 60
    return if (absMins == 0) "$sign $absHours"
    else String.format(Locale.ROOT, "%s %d:%02d", sign, absHours, absMins)
}

/**
 * Saved timezone configuration. Abbreviation is DST-aware: each zone declares both
 * its standard-time and daylight-time short names; the right one is chosen at
 * render time based on whether DST is in effect.
 */
data class SavedZone(
    val zoneId: ZoneId,
    val standardAbbr: String,
    val daylightAbbr: String,
    val flagEmoji: String,
) {
    fun abbreviationAt(at: ZonedDateTime): String {
        val zoned = at.withZoneSameInstant(zoneId)
        return if (zoneId.rules.isDaylightSavings(zoned.toInstant())) daylightAbbr else standardAbbr
    }
}

object TimezoneTileConfig {

    // Saved set, ordered by UTC offset west-to-east.
    val SAVED_ZONES: List<SavedZone> = listOf(
        // Pacific (US): PST (UTC-8) ↔ PDT (UTC-7)
        SavedZone(ZoneId.of("America/Los_Angeles"), "PST", "PDT", "🇺🇸"),
        // Costa Rica (no DST): UTC-6 — labelled "PVT" (Pura Vida Time) per the
        // owner's personal naming convention. Stored as both std and dst since
        // Costa Rica doesn't observe DST.
        SavedZone(ZoneId.of("America/Costa_Rica"),  "PVT", "PVT", "🇨🇷"),
        // Brazil (no DST since 2019): BRT UTC-3
        SavedZone(ZoneId.of("America/Sao_Paulo"),   "BRT", "BRT", "🇧🇷"),
        // UK: GMT (UTC+0) ↔ BST (UTC+1)
        SavedZone(ZoneId.of("Europe/London"),       "GMT", "BST", "🇬🇧"),
        // Continental Europe: CET (UTC+1) ↔ CEST (UTC+2). EU flag because the
        // Maastricht/Amsterdam zone covers most of continental EU.
        SavedZone(ZoneId.of("Europe/Amsterdam"),    "CET", "CEST", "🇪🇺"),
        // India (no DST): IST UTC+5:30
        SavedZone(ZoneId.of("Asia/Kolkata"),        "IST", "IST", "🇮🇳"),
        // China (no DST): CST UTC+8
        SavedZone(ZoneId.of("Asia/Shanghai"),       "CST",  "CST",  "🇨🇳"),
        // New Zealand: NZST UTC+12 ↔ NZDT UTC+13
        SavedZone(ZoneId.of("Pacific/Auckland"),    "NZST", "NZDT", "🇳🇿"),
    )

    /**
     * Extended set used by the scrollable [com.guil.globetrotting.zones.ZonesActivity].
     * Curated for the owner's actual travel patterns — west-to-east, no Africa
     * (Lagos/Cairo/Johannesburg dropped), no SE-Asia secondaries (Tehran/Karachi/
     * Jakarta/Manila dropped), no Greece/Turkey/Iceland. The tile shows the
     * top 8 (SAVED_ZONES); tap-to-open reveals this fuller list.
     */
    val ALL_ZONES: List<SavedZone> = listOf(
        SavedZone(ZoneId.of("Pacific/Honolulu"),                    "HST",  "HST",  "🇺🇸"),
        SavedZone(ZoneId.of("America/Anchorage"),                   "AKST", "AKDT", "🇺🇸"),
        SavedZone(ZoneId.of("America/Los_Angeles"),                 "PST",  "PDT",  "🇺🇸"),
        SavedZone(ZoneId.of("America/Phoenix"),                     "MST",  "MST",  "🇺🇸"),
        SavedZone(ZoneId.of("America/Denver"),                      "MST",  "MDT",  "🇺🇸"),
        SavedZone(ZoneId.of("America/Mexico_City"),                 "CST",  "CDT",  "🇲🇽"),
        SavedZone(ZoneId.of("America/Costa_Rica"),                  "PVT",  "PVT",  "🇨🇷"),
        SavedZone(ZoneId.of("America/Chicago"),                     "CST",  "CDT",  "🇺🇸"),
        SavedZone(ZoneId.of("America/New_York"),                    "EST",  "EDT",  "🇺🇸"),
        SavedZone(ZoneId.of("America/Toronto"),                     "EST",  "EDT",  "🇨🇦"),
        SavedZone(ZoneId.of("America/Sao_Paulo"),                   "BRT",  "BRT",  "🇧🇷"),
        SavedZone(ZoneId.of("America/Argentina/Buenos_Aires"),      "ART",  "ART",  "🇦🇷"),
        SavedZone(ZoneId.of("Europe/London"),                       "GMT",  "BST",  "🇬🇧"),
        SavedZone(ZoneId.of("Europe/Lisbon"),                       "WET",  "WEST", "🇵🇹"),
        SavedZone(ZoneId.of("Europe/Amsterdam"),                    "CET",  "CEST", "🇪🇺"),
        SavedZone(ZoneId.of("Europe/Moscow"),                       "MSK",  "MSK",  "🇷🇺"),
        SavedZone(ZoneId.of("Asia/Dubai"),                          "GST",  "GST",  "🇦🇪"),
        SavedZone(ZoneId.of("Asia/Kolkata"),                        "IST",  "IST",  "🇮🇳"),
        SavedZone(ZoneId.of("Asia/Bangkok"),                        "ICT",  "ICT",  "🇹🇭"),
        SavedZone(ZoneId.of("Asia/Singapore"),                      "SGT",  "SGT",  "🇸🇬"),
        SavedZone(ZoneId.of("Asia/Hong_Kong"),                      "HKT",  "HKT",  "🇭🇰"),
        SavedZone(ZoneId.of("Asia/Shanghai"),                       "CST",  "CST",  "🇨🇳"),
        SavedZone(ZoneId.of("Asia/Seoul"),                          "KST",  "KST",  "🇰🇷"),
        SavedZone(ZoneId.of("Asia/Tokyo"),                          "JST",  "JST",  "🇯🇵"),
        SavedZone(ZoneId.of("Australia/Perth"),                     "AWST", "AWST", "🇦🇺"),
        SavedZone(ZoneId.of("Australia/Sydney"),                    "AEST", "AEDT", "🇦🇺"),
        SavedZone(ZoneId.of("Pacific/Auckland"),                    "NZST", "NZDT", "🇳🇿"),
    )

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
        "America/Costa_Rica" -> "🇨🇷"
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
