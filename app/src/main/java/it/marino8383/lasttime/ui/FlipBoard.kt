package it.marino8383.lasttime.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.marino8383.lasttime.AppSettings
import it.marino8383.lasttime.breakdown
import it.marino8383.lasttime.data.Counter
import java.util.Locale

// Palette Solari da stazione (mockup v14)
private val BoardBg = Color(0xFF1C1F24)
private val TileBg = Color(0xFF080909)
private val HalfBg = Color(0xFF101216)
private val FlapBg = Color(0xFF1D2127)
private val DigitWhite = Color(0xFFF4F5F1)
private val DigitOver = Color(0xFFFFB84D)

private val flipUnits = listOf(
    "SPEZZATO" to "Spezzato",
    "ANNI" to "Anni",
    "MESI" to "Mesi",
    "GIORNI" to "Giorni",
    "MINUTI" to "Minuti",
    "SECONDI" to "Secondi",
)

/** Vista tabellone Solari: nessun controllo sui board, doppio tap = riparti (v20). */
@Composable
fun FlipView(
    counters: List<Counter>,
    now: Long,
    onBoardDoubleTap: (Counter) -> Unit,
) {
    val context = LocalContext.current
    var unit by remember { mutableStateOf(AppSettings.flipUnit(context)) }
    var showYears by remember { mutableStateOf(AppSettings.flipShowYears(context)) }
    var showSeconds by remember { mutableStateOf(AppSettings.flipShowSeconds(context)) }

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            flipUnits.forEach { (key, label) ->
                FilterChip(
                    selected = unit == key,
                    onClick = {
                        unit = key
                        AppSettings.setFlipUnit(context, key)
                    },
                    label = { Text(label, fontSize = 11.sp) },
                )
            }
        }
        if (unit == "SPEZZATO") {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = showYears,
                    onClick = {
                        showYears = !showYears
                        AppSettings.setFlipShowYears(context, showYears)
                    },
                    label = { Text("Mostra anni", fontSize = 11.sp) },
                )
                FilterChip(
                    selected = showSeconds,
                    onClick = {
                        showSeconds = !showSeconds
                        AppSettings.setFlipShowSeconds(context, showSeconds)
                    },
                    label = { Text("Mostra secondi", fontSize = 11.sp) },
                )
            }
        }

        LazyColumn(contentPadding = PaddingValues(16.dp, 6.dp, 16.dp, 30.dp)) {
            items(counters, key = { it.id }) { counter ->
                FlipBoard(
                    counter = counter,
                    now = now,
                    unit = unit,
                    showYears = showYears,
                    showSeconds = showSeconds,
                    onDoubleTap = { onBoardDoubleTap(counter) },
                )
            }
        }
    }
}

@Composable
private fun FlipBoard(
    counter: Counter,
    now: Long,
    unit: String,
    showYears: Boolean,
    showSeconds: Boolean,
    onDoubleTap: () -> Unit,
) {
    val elapsedMs = (now - counter.startMs).coerceAtLeast(0)
    val totalSec = elapsedMs / 1000
    val snoozePending = counter.snoozeUntilMs?.takeIf { it > now }
    val over = counter.bellEnabled && counter.bellNotified && snoozePending == null

    val groups: List<Pair<String, String>> = when (unit) {
        "ANNI" -> listOf("anni" to String.format(Locale.ITALIAN, "%.4f", totalSec / 31_536_000.0))
        "MESI" -> listOf("mesi" to (totalSec / 2_592_000).toString())
        "GIORNI" -> listOf("giorni" to (totalSec / 86_400).toString())
        "MINUTI" -> listOf("min" to (totalSec / 60).toString())
        "SECONDI" -> listOf("sec" to totalSec.toString())
        else -> {
            val b = breakdown(elapsedMs)
            buildList {
                // gruppo anni auto-rimosso se a zero (v14)
                if (showYears && b.years > 0) {
                    add("anni" to String.format(Locale.ITALIAN, "%02d", b.years.coerceAtMost(99)))
                }
                add("giorni" to String.format(Locale.ITALIAN, "%03d", b.days))
                add("ore" to String.format(Locale.ITALIAN, "%02d", b.hours))
                add("min" to String.format(Locale.ITALIAN, "%02d", b.minutes))
                if (showSeconds) {
                    add("sec" to String.format(Locale.ITALIAN, "%02d", b.seconds))
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(BoardBg)
            .pointerInput(counter.id) {
                detectTapGestures(onDoubleTap = { onDoubleTap() })
            }
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                counter.name.uppercase(),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.5.sp,
                color = Color(0xFFE8E9E6),
                modifier = Modifier.weight(1f),
            )
            if (over) {
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text(
                        "SFORATO",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val totalTiles = groups.sumOf { it.second.length }
            val groupCount = groups.size
            val interGroup = 12.dp
            val intraTile = 3.dp
            val available =
                maxWidth - interGroup * (groupCount - 1) - intraTile * (totalTiles - groupCount)
            // le placche si ridimensionano per riempire la riga (v14)
            val tileW = (available / totalTiles).coerceIn(14.dp, 32.dp)
            val tileH = tileW * 1.7f

            Row(
                horizontalArrangement = Arrangement.spacedBy(interGroup),
                modifier = Modifier.align(Alignment.Center),
            ) {
                groups.forEach { (label, digits) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(intraTile)) {
                            digits.forEach { ch ->
                                FlipTile(digit = ch, w = tileW, h = tileH, over = over)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            label.uppercase(),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            color = Color(0xFF7A8089),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Placca split-flap: la metà superiore della cifra vecchia "cade" (scaleY 1→0 sul
 * cardine centrale), poi la metà inferiore della nuova si apre (scaleY 0→1).
 * Animazione 2D come nella v9 del mockup: compatibile ovunque.
 */
@Composable
private fun FlipTile(digit: Char, w: Dp, h: Dp, over: Boolean) {
    var current by remember { mutableStateOf(digit) }
    var previous by remember { mutableStateOf(digit) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(digit) {
        if (digit != current) {
            previous = current
            current = digit
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec = tween(280, easing = LinearOutSlowInEasing))
        }
    }
    val p = progress.value
    val color = if (over) DigitOver else DigitWhite

    Box(
        Modifier
            .size(w, h)
            .clip(RoundedCornerShape(5.dp))
            .background(TileBg)
    ) {
        HalfDigit(current, top = true, w, h, color, HalfBg, Modifier.align(Alignment.TopCenter))
        HalfDigit(
            if (p < 1f) previous else current,
            top = false, w, h, color, HalfBg,
            Modifier.align(Alignment.BottomCenter),
        )
        if (p < 0.5f) {
            HalfDigit(
                previous, top = true, w, h, color, FlapBg,
                Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        scaleY = 1f - p * 2f
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    },
            )
        } else if (p < 1f) {
            HalfDigit(
                current, top = false, w, h, color, FlapBg,
                Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        scaleY = (p - 0.5f) * 2f
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    },
            )
        }
        HorizontalDivider(
            Modifier.align(Alignment.Center),
            thickness = 1.dp,
            color = Color.Black,
        )
    }
}

@Composable
private fun HalfDigit(
    d: Char,
    top: Boolean,
    w: Dp,
    h: Dp,
    color: Color,
    bg: Color,
    modifier: Modifier,
) {
    Box(
        modifier
            .size(w, h / 2)
            .clipToBounds()
            .background(bg)
    ) {
        Box(
            Modifier
                .size(w, h)
                .offset(y = if (top) 0.dp else -(h / 2)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                d.toString(),
                color = color,
                fontSize = w.value.sp,
                fontWeight = FontWeight.ExtraBold,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
        }
    }
}
