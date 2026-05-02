package com.guil.globetrotting.tile

import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

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
    val cityOverride: String? = null,
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
        // Eastern (US): EST (UTC-5) ↔ EDT (UTC-4)
        SavedZone(ZoneId.of("America/New_York"),    "EST", "EDT", "🇺🇸"),
        // Brazil (no DST since 2019): BRT UTC-3
        SavedZone(ZoneId.of("America/Sao_Paulo"),   "BRT", "BRT", "🇧🇷"),
        // UK: GMT (UTC+0) ↔ BST (UTC+1)
        SavedZone(ZoneId.of("Europe/London"),       "GMT", "BST", "🇬🇧"),
        // Continental Europe: CET (UTC+1) ↔ CEST (UTC+2). EU flag because the
        // Maastricht/Amsterdam zone covers most of continental EU.
        SavedZone(ZoneId.of("Europe/Amsterdam"),    "CET", "CEST", "🇪🇺", cityOverride = "MAASTRICHT"),
        // India (no DST): IST UTC+5:30
        SavedZone(ZoneId.of("Asia/Kolkata"),        "IST", "IST", "🇮🇳"),
        // China (no DST): CST UTC+8
        SavedZone(ZoneId.of("Asia/Shanghai"),       "CST",  "CST",  "🇨🇳"),
        // New Zealand: NZST UTC+12 ↔ NZDT UTC+13
        SavedZone(ZoneId.of("Pacific/Auckland"),    "NZST", "NZDT", "🇳🇿"),
    )

    /** Best-effort city name from a ZoneId — last path segment, prettified. */
    fun cityFromZone(zoneId: ZoneId): String =
        zoneId.id.substringAfterLast('/')
            .replace('_', ' ')
            .uppercase(Locale.ENGLISH)

    /**
     * For the CET/CEST saved row: if the user's local zone is also in the CET cluster,
     * surface that specific country's flag instead of the EU default. Falls back to EU
     * when the local zone isn't a recognised CET/CEST country.
     */
    fun resolveCetFlag(localZone: ZoneId): String = when (localZone.id) {
        "Europe/Amsterdam" -> "🇳🇱"
        "Europe/Berlin" -> "🇩🇪"
        "Europe/Paris" -> "🇫🇷"
        "Europe/Madrid" -> "🇪🇸"
        "Europe/Rome" -> "🇮🇹"
        "Europe/Vienna" -> "🇦🇹"
        "Europe/Prague" -> "🇨🇿"
        "Europe/Warsaw" -> "🇵🇱"
        "Europe/Brussels" -> "🇧🇪"
        "Europe/Copenhagen" -> "🇩🇰"
        "Europe/Stockholm" -> "🇸🇪"
        "Europe/Oslo" -> "🇳🇴"
        "Europe/Zurich" -> "🇨🇭"
        "Europe/Luxembourg" -> "🇱🇺"
        "Europe/Budapest" -> "🇭🇺"
        "Europe/Bratislava" -> "🇸🇰"
        "Europe/Ljubljana" -> "🇸🇮"
        "Europe/Zagreb" -> "🇭🇷"
        "Europe/Belgrade" -> "🇷🇸"
        "Europe/Sarajevo" -> "🇧🇦"
        "Europe/Skopje" -> "🇲🇰"
        "Europe/Tirane" -> "🇦🇱"
        "Europe/Andorra" -> "🇦🇩"
        "Europe/Malta" -> "🇲🇹"
        "Europe/Monaco" -> "🇲🇨"
        "Europe/San_Marino" -> "🇸🇲"
        "Europe/Vatican" -> "🇻🇦"
        "Europe/Vaduz" -> "🇱🇮"
        "Africa/Tunis" -> "🇹🇳"
        "Africa/Algiers" -> "🇩🇿"
        else -> "🇪🇺"
    }

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
