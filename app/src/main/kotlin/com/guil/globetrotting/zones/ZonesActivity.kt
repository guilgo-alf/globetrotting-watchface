package com.guil.globetrotting.zones

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.Text
import com.guil.globetrotting.R
import com.guil.globetrotting.tile.TimezoneTileConfig
import com.guil.globetrotting.tile.formatOffset
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Full scrollable timezone list. Launched by tapping the Tile.
 *
 * Uses Wear Compose's `ScalingLazyColumn` so:
 *   - rows scale (fisheye) at top/bottom for the round-screen look
 *   - the rotating bezel on Galaxy Watch 6 Classic scrolls natively (wired via
 *     `Modifier.rotaryScrollable` + an explicit `FocusRequester` that grabs focus
 *     in a `LaunchedEffect`)
 *
 * Re-renders every minute on a coroutine tick — same minute granularity as the tile.
 */
class ZonesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Round-screen edge-to-edge: let Compose own the entire canvas, no system insets.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { ZonesScreen() }
    }
}

private val OpenDyslexic = FontFamily(Font(R.font.opendyslexic_regular, FontWeight.Normal))

private val Foreground = Color(0xFFEAEAEA)
private val DimRail = Color(0xFF303030)
private val WorkAmber = Color(0xFFE8C77A)
private val WorkAmberBright = Color(0xFFFFD78A)
// Softened from 0x22 -> 0x14 so the pill no longer dominates non-highlighted
// rows. Comes paired with a smaller corner radius for the same reason.
private val PillTint = Color(0x14EAEAEA)

@Composable
private fun ZonesScreen() {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        // Re-tick every minute aligned to the wall-clock minute boundary so the
        // now-dot and times stay current. The coroutine is automatically cancelled
        // when the composition leaves (activity finishes / rotates).
        while (true) {
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
            now = ZonedDateTime.now()
        }
    }

    val localZone = ZoneId.systemDefault()
    val homeMinute = now.withZoneSameInstant(ZoneId.of("Europe/Amsterdam")).minute
    val localOffsetSeconds = now.offset.totalSeconds

    // Pick the single index to highlight + initially scroll to. Same priority logic
    // as the tile in ExtrasTileService.buildViewModel:
    //   1. exact zone-id match (e.g. local=Amsterdam, saved CEST=Amsterdam)
    //   2. first row with matching offset (e.g. local=Berlin, both +2)
    //   3. fall back to middle of the list so content surrounds the centre
    // Activity shows the SAME 8 saved zones as the tile — the rotating-bezel scroll
    // is just a different viewing affordance, not a different data set.
    val highlightIndex = remember(localZone) {
        val zones = TimezoneTileConfig.SAVED_ZONES
        val exact = zones.indexOfFirst { it.zoneId == localZone }
        if (exact != -1) return@remember exact
        val sameOffset = zones.indexOfFirst {
            ZonedDateTime.now(it.zoneId).offset.totalSeconds == localOffsetSeconds
        }
        if (sameOffset != -1) sameOffset else zones.size / 2
    }
    val state = rememberScalingLazyListState(initialCenterItemIndex = highlightIndex)
    val focusRequester = remember { FocusRequester() }

    // Grab focus once on entry so the rotating bezel feeds events into the column.
    // Without this the bezel rotates but no scroll happens.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        ScalingLazyColumn(
            state = state,
            // Round bezel safe area — bigger top/bottom padding because rows there are
            // visually clipped by the screen curve regardless of horizontal padding.
            // Tight horizontal padding because rows at the equator have ~240dp to play
            // with on a 480px round screen, and we need every column we can fit.
            // Symmetric horizontal padding centres rows on the round screen. Smaller
            // top/bottom because ScalingLazyColumn's fisheye scaling already absorbs
            // the bezel curve at the poles — gains us one extra visible row.
            contentPadding = PaddingValues(
                start = 6.dp,
                end = 6.dp,
                top = 48.dp,
                bottom = 48.dp,
            ),
            // 0dp because each row already has 7dp vertical padding — adding more
            // here would make the list feel sparse for no readability gain.
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier
                .fillMaxSize()
                .rotaryScrollable(
                    behavior = RotaryScrollableDefaults.behavior(scrollableState = state),
                    focusRequester = focusRequester,
                ),
        ) {
            itemsIndexed(TimezoneTileConfig.SAVED_ZONES) { index, zone ->
                val zoned = now.withZoneSameInstant(zone.zoneId)
                val dimMinutes = zoned.minute == homeMinute
                ZoneRow(
                    abbreviation = zone.abbreviationAt(now),
                    flag = zone.flagEmoji,
                    zoned = zoned,
                    offsetSeconds = zoned.offset.totalSeconds - localOffsetSeconds,
                    // Exclusive: only the single resolved highlightIndex gets the pill,
                    // so SAST/CEST at the same offset don't both light up.
                    highlighted = index == highlightIndex,
                    dimMinutes = dimMinutes,
                )
            }
        }
    }
}

@Composable
private fun ZoneRow(
    abbreviation: String,
    flag: String,
    zoned: ZonedDateTime,
    offsetSeconds: Int,
    highlighted: Boolean,
    dimMinutes: Boolean,
) {
    val rowBackground = if (highlighted) PillTint else Color.Transparent
    val codeColor = Foreground
    val offsetColor = if (highlighted) Foreground else DimRail

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            // Slightly tighter corner so the pill feels like an underlay rather than
            // a separate element competing with the row.
            .clip(RoundedCornerShape(10.dp))
            .background(rowBackground)
            // Vertical 5dp — tight enough that consecutive rows feel like a list,
            // loose enough to clear OpenDyslexic's ascenders. Combined with 0dp
            // inter-row spacing the rhythm comes from the row itself, not gaps.
            .padding(horizontal = 6.dp, vertical = 5.dp),
    ) {
        // Code column. sp=10 + 46dp width fits 4-letter codes (AKDT, NZDT, AEDT)
        // without wrap. Bold removed from non-time text so the pill row doesn't
        // double-up its emphasis (pill background + bold + bigger time).
        Text(
            text = abbreviation,
            color = codeColor,
            style = textStyle(6),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            softWrap = false,
            // 2dp end-padding keeps a consistent gap to the flag regardless of
            // whether the code is 3 or 4 characters — the actual fix for the
            // "alignment feels off" perception.
            modifier = Modifier.width(46.dp).padding(end = 2.dp),
        )
        Spacer(Modifier.width(5.dp))
        // Flag — 20dp absorbs the slight width variance between flag emojis
        // (🇪🇺 wider than 🇬🇧) so the time column's left edge stays steady.
        Text(
            text = flag,
            color = Foreground,
            style = textStyle(6),
            modifier = Modifier.width(20.dp),
        )
        Spacer(Modifier.width(4.dp))
        // Time — primary value, sp=13 (one notch up from supporting metadata).
        TimeText(zoned = zoned, dimMinutes = dimMinutes, highlighted = highlighted)
        Spacer(Modifier.width(6.dp))
        // Band visualisation — same semantics as the tile band.
        BandCanvas(zoned = zoned, highlighted = highlighted)
        Spacer(Modifier.width(4.dp))
        // Offset relative to local. Empty for the local row. 26dp fits "+12" / "− 1".
        Text(
            text = formatOffset(offsetSeconds),
            color = offsetColor,
            style = textStyle(6),
            textAlign = TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            softWrap = false,
            modifier = Modifier.width(26.dp),
        )
    }
}

@Composable
private fun TimeText(zoned: ZonedDateTime, dimMinutes: Boolean, highlighted: Boolean) {
    val hourStr = zoned.hour.toString()
    val mmStr = String.format(Locale.ROOT, "%02d", zoned.minute)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // 34dp accommodates the worst-case "1142" (4 chars at sp=13 OpenDyslexic).
        modifier = Modifier.width(34.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        // sp=13 makes the time the largest type element on the row — the primary
        // value the user is reading. Bold is reserved for the highlighted row's time
        // (and only the time, not the code, so the pill row doesn't feel double-emphasised).
        Text(
            text = hourStr,
            color = Foreground,
            style = textStyle(9, bold = highlighted),
        )
        Text(
            text = mmStr,
            // Highlighted row always shows minutes in the primary colour — skipping
            // the dim rule because the highlighted row trivially matches the home
            // minute when it IS the home zone, and we don't want to hide its mins.
            color = if (dimMinutes && !highlighted) Color.Black else Foreground,
            style = textStyle(9, bold = highlighted),
        )
    }
}

/**
 * Compose-side band: same shape as the tile bitmap but drawn directly via Canvas
 * so we don't have to round-trip through bitmap encoding for an in-app screen.
 *
 * Visual semantics MUST stay in sync with `ExtrasTileRenderer.bandResource`:
 *   - rail #303030
 *   - work window 9-17 in warm amber (#E8C77A standard / #FFD78A highlighted)
 *   - now-dot black between 09:00 and 16:30 (deep-work), white otherwise
 */
@Composable
private fun BandCanvas(zoned: ZonedDateTime, highlighted: Boolean) {
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .size(width = 38.dp, height = 10.dp)
            .clip(RoundedCornerShape(2.dp)),
    ) {
        val w = size.width
        val h = size.height
        // Rail.
        drawRect(color = DimRail, size = Size(w, h))

        // Working hours window 9-17.
        val workStart = (9f / 24f) * w
        val workEnd = (17f / 24f) * w
        val workColor = if (highlighted) WorkAmberBright else WorkAmber
        drawRoundRect(
            color = workColor,
            topLeft = Offset(workStart, 0.5f),
            size = Size(workEnd - workStart, h - 1f),
            cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
        )

        // Now-dot. Black between 09:00 and 16:30 (deep-work window — 30 min before
        // the amber bar ends so the wind-down phase reads as "available again").
        val nowFraction = (zoned.hour + zoned.minute / 60f) / 24f
        val nowX = nowFraction * w
        val dotRadius = 2.5.dp.toPx()
        val isDeepWork = nowFraction in (9f / 24f)..(16.5f / 24f)
        val (dotFill, dotStroke) = if (isDeepWork) Color.Black to Color.Black
        else Color.White to Color.Black
        drawCircle(color = dotFill, radius = dotRadius, center = Offset(nowX, h / 2f))
        drawCircle(
            color = dotStroke,
            radius = dotRadius,
            center = Offset(nowX, h / 2f),
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}

private fun textStyle(sizeSp: Int, bold: Boolean = false): TextStyle =
    TextStyle(
        fontFamily = OpenDyslexic,
        fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal,
        fontSize = sizeSp.sp,
    )
