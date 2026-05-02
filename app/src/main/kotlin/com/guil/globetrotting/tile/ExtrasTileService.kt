package com.guil.globetrotting.tile

import androidx.concurrent.futures.ResolvableFuture
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import java.time.ZoneId
import java.time.ZonedDateTime

class ExtrasTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> {
        val viewModel = buildViewModel()
        val version = currentVersion()

        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(
                        LayoutElementBuilders.Layout.Builder()
                            .setRoot(ExtrasTileRenderer.build(viewModel))
                            .build(),
                    )
                    .build(),
            )
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(version)
            .setFreshnessIntervalMillis(FRESHNESS_INTERVAL_MS)
            .setTileTimeline(timeline)
            .build()

        return ResolvableFuture.create<TileBuilders.Tile>().apply { set(tile) }
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> {
        val viewModel = buildViewModel()
        val version = requestParams.version.ifEmpty { currentVersion() }
        val resources = ExtrasTileRenderer.buildResources(viewModel, version)
        return ResolvableFuture.create<ResourceBuilders.Resources>().apply { set(resources) }
    }

    private fun buildViewModel(): ExtrasTileRenderer.TileViewModel {
        val now = ZonedDateTime.now()
        val localZone = ZoneId.systemDefault()
        val saved = TimezoneTileConfig.SAVED_ZONES
        val localOffsetSeconds = now.offset.totalSeconds
        val isInSaved = saved.any { it.zoneId == localZone }

        // Reference minute = home (CET/CEST) zone's current minute. Rows that share
        // this minute have their :mm rendered dimmer to reduce visual repetition.
        val cestMinute = saved.firstOrNull { it.zoneId.id == "Europe/Amsterdam" }
            ?.let { now.withZoneSameInstant(it.zoneId).minute }

        val rows = saved.map { zone ->
            val zoned = now.withZoneSameInstant(zone.zoneId)
            val abbr = zone.abbreviationAt(now)
            ExtrasTileRenderer.RowData(
                zoneId = zone.zoneId,
                abbreviation = abbr,
                flag = zone.flagEmoji,
                zonedTime = zoned,
                offsetSeconds = zoned.offset.totalSeconds - localOffsetSeconds,
                highlighted = false,  // resolved below
                dimmed = false,
                bandResourceId = "band_${abbr.lowercase()}",
                // Dim :mm whenever it echoes the home-zone minute. Applies to home
                // (CET/CEST) too — its minutes are the reference, so they're noisy by
                // definition and benefit from being quieter.
                dimMinutes = cestMinute != null && zoned.minute == cestMinute,
            )
        }.toMutableList()

        // Pick which row to highlight, in priority order:
        //   1. Saved row whose ZoneId matches the user's local zone (e.g. local = Amsterdam, saved CEST = Amsterdam)
        //   2. Saved row that currently has the same UTC offset (e.g. local = Berlin, saved Amsterdam → both CEST = +2)
        //   3. None match → insert a new row for the local zone at its sorted position
        val exactIdx = rows.indexOfFirst { it.zoneId == localZone }
        when {
            exactIdx != -1 ->
                rows[exactIdx] = rows[exactIdx].copy(highlighted = true)
            else -> {
                val sameOffsetIdx = rows.indexOfFirst { it.zonedTime.offset.totalSeconds == localOffsetSeconds }
                if (sameOffsetIdx != -1) {
                    rows[sameOffsetIdx] = rows[sameOffsetIdx].copy(highlighted = true)
                } else {
                    val localRow = ExtrasTileRenderer.RowData(
                        zoneId = localZone,
                        abbreviation = abbreviationFor(localZone, now),
                        flag = TimezoneTileConfig.flagFromZone(localZone),
                        zonedTime = now,
                        offsetSeconds = 0,
                        highlighted = true,
                        dimmed = false,
                        bandResourceId = "band_local",
                    )
                    val insertIdx = rows.indexOfFirst { it.zonedTime.offset.totalSeconds > localOffsetSeconds }
                    if (insertIdx == -1) rows.add(localRow) else rows.add(insertIdx, localRow)

                    // Drop NZST (Auckland) to keep the row count constant when a new
                    // travel row is added — prevents the tile from getting too tall.
                    rows.removeAll { it.zoneId.id == "Pacific/Auckland" }
                }
            }
        }

        return ExtrasTileRenderer.TileViewModel(
            headerTime = "",
            headerSub = "",
            rows = rows,
            travelRow = null,
            travelCaption = null,
        )
    }

    /**
     * Timezone abbreviation with layered fallbacks. Android's tzdata SHORT names are
     * unreliable post-2018f and often return "GMT+2"-style strings, so we try (in order):
     *   1. java.util.TimeZone short name (CEST/JST/EDT…)
     *   2. Curated map for common European/Asian zones the user travels to
     *   3. Initials from the LONG localised name ("Central European Summer Time" → CEST)
     *   4. ISO-style "UTC+02" — clearly a fallback rather than pseudo-named
     */
    private fun abbreviationFor(zoneId: ZoneId, at: ZonedDateTime): String {
        val zoned = at.withZoneSameInstant(zoneId)
        val daylight = zoneId.rules.isDaylightSavings(zoned.toInstant())

        // 1. java.util.TimeZone SHORT — works for many zones in modern Android tzdata.
        val tz = java.util.TimeZone.getTimeZone(zoneId.id)
        val shortName = tz.getDisplayName(daylight, java.util.TimeZone.SHORT, java.util.Locale.ENGLISH)
        if (shortName.isNotEmpty() && !shortName.startsWith("GMT") && !shortName.startsWith("UTC") && shortName.length <= 5) {
            return shortName
        }

        // 2. Curated lookup for common zones (covers the European corridor where shortName fails).
        CURATED_ABBR[zoneId.id]?.let { (std, dst) ->
            return if (daylight) dst else std
        }

        // 3. Initials from the LONG English display name.
        val longName = tz.getDisplayName(daylight, java.util.TimeZone.LONG, java.util.Locale.ENGLISH)
        if (longName.isNotEmpty() && !longName.startsWith("GMT") && !longName.startsWith("UTC")) {
            val initials = longName.split(' ', '-')
                .mapNotNull { it.firstOrNull()?.takeIf(Char::isUpperCase) }
                .joinToString("")
            if (initials.length in 2..5) return initials
        }

        // 4. ISO-style UTC offset.
        val totalSec = zoned.offset.totalSeconds
        val sign = if (totalSec < 0) "-" else "+"
        val hh = Math.abs(totalSec) / 3600
        val mm = (Math.abs(totalSec) % 3600) / 60
        return if (mm == 0) String.format(java.util.Locale.ROOT, "UTC%s%02d", sign, hh)
        else String.format(java.util.Locale.ROOT, "UTC%s%02d:%02d", sign, hh, mm)
    }

    /** Bumped each minute so the bands re-render with updated dot positions. */
    private fun currentVersion(): String =
        (System.currentTimeMillis() / FRESHNESS_INTERVAL_MS).toString()

    companion object {
        private const val FRESHNESS_INTERVAL_MS = 60_000L

        /** Curated zone-id → (standard, daylight) abbreviation pairs. */
        private val CURATED_ABBR: Map<String, Pair<String, String>> = mapOf(
            "Europe/Amsterdam" to ("CET" to "CEST"),
            "Europe/Berlin" to ("CET" to "CEST"),
            "Europe/Paris" to ("CET" to "CEST"),
            "Europe/Madrid" to ("CET" to "CEST"),
            "Europe/Rome" to ("CET" to "CEST"),
            "Europe/Vienna" to ("CET" to "CEST"),
            "Europe/Prague" to ("CET" to "CEST"),
            "Europe/Warsaw" to ("CET" to "CEST"),
            "Europe/Stockholm" to ("CET" to "CEST"),
            "Europe/London" to ("GMT" to "BST"),
            "Europe/Lisbon" to ("WET" to "WEST"),
            "Europe/Athens" to ("EET" to "EEST"),
            "Europe/Helsinki" to ("EET" to "EEST"),
            "Europe/Moscow" to ("MSK" to "MSK"),
            "America/New_York" to ("EST" to "EDT"),
            "America/Chicago" to ("CST" to "CDT"),
            "America/Denver" to ("MST" to "MDT"),
            "America/Phoenix" to ("MST" to "MST"),
            "America/Los_Angeles" to ("PST" to "PDT"),
            "America/Anchorage" to ("AKST" to "AKDT"),
            "America/Honolulu" to ("HST" to "HST"),
            "America/Toronto" to ("EST" to "EDT"),
            "America/Mexico_City" to ("CST" to "CDT"),
            "America/Sao_Paulo" to ("BRT" to "BRT"),
            "America/Argentina/Buenos_Aires" to ("ART" to "ART"),
            "Asia/Tokyo" to ("JST" to "JST"),
            "Asia/Shanghai" to ("CST" to "CST"),
            "Asia/Hong_Kong" to ("HKT" to "HKT"),
            "Asia/Singapore" to ("SGT" to "SGT"),
            "Asia/Seoul" to ("KST" to "KST"),
            "Asia/Kolkata" to ("IST" to "IST"),
            "Asia/Dubai" to ("GST" to "GST"),
            "Asia/Bangkok" to ("ICT" to "ICT"),
            "Australia/Sydney" to ("AEST" to "AEDT"),
            "Australia/Perth" to ("AWST" to "AWST"),
            "Pacific/Auckland" to ("NZST" to "NZDT"),
        )
    }
}
