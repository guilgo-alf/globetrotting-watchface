package com.guil.globetrotting.watchface.data

import java.time.ZoneId

data class TimezoneRow(
    val zoneId: ZoneId,
    val abbreviation: String,
    val isLocal: Boolean = false,
    val showMinutes: Boolean = false,
)

val DEFAULT_ZONES: List<TimezoneRow> = listOf(
    TimezoneRow(ZoneId.of("America/Los_Angeles"), "PDT"),
    TimezoneRow(ZoneId.of("America/Phoenix"),     "PVT"),
    TimezoneRow(ZoneId.of("America/New_York"),    "EDT"),
    TimezoneRow(ZoneId.of("America/Sao_Paulo"),   "BRT"),
    TimezoneRow(ZoneId.of("Europe/Amsterdam"),    "CEST", isLocal = true),
    TimezoneRow(ZoneId.of("Asia/Kolkata"),        "IST",  showMinutes = true),
    TimezoneRow(ZoneId.of("Asia/Shanghai"),       "CST"),
)

sealed class CellContent {
    data class Zone(val zone: TimezoneRow) : CellContent()
    object DateLabel : CellContent()
}

data class GridCell(val col: Int, val row: Int, val content: CellContent)

// Stripped to date-only — user removed the timezone grid earlier and tried bringing
// it back didn't read well. Zones live in the Tile now. DEFAULT_ZONES kept above
// for reference / future re-add.
val GRID_CELLS: List<GridCell> = listOf(
    GridCell(col = 0, row = 0, content = CellContent.DateLabel),
)

object GridMetrics {
    const val ORIGIN_X = 60f
    const val ORIGIN_Y = 339f
    const val COL_WIDTH = 115f
    const val GUTTER = 6f
    const val ROW_HEIGHT = 20f
    const val COLUMNS = 3
}
