package it.marino8383.lasttime.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import it.marino8383.lasttime.ui.theme.SwipeArchiveBg
import it.marino8383.lasttime.ui.theme.SwipeArchiveFg
import it.marino8383.lasttime.ui.theme.SwipeDeleteBg
import it.marino8383.lasttime.ui.theme.SwipeDeleteFg
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val SwipeMax = 150.dp
private val SwipeThreshold = 90.dp
private val CardRadius = 26.dp

/**
 * Card trascinabile in orizzontale (v23): swipe a destra = elimina, a sinistra = archivia.
 * Sotto compare l'underlay colorato con l'azione che scatterebbe.
 *
 * Lo scroll verticale resta alla lista: detectHorizontalDragGestures parte solo dopo lo
 * slop orizzontale, quindi un dito che scende non viene intercettato. Allo stesso modo il
 * doppio tap della card sopravvive, perché un drag oltre lo slop fa fallire il tap senza
 * consumare i movimenti.
 *
 * [onSwipeArchive] a null = niente archiviazione (le card già archiviate si possono solo eliminare).
 */
@Composable
fun SwipeableCard(
    onSwipeDelete: () -> Unit,
    onSwipeArchive: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current
    val maxPx = with(density) { SwipeMax.toPx() }
    val thresholdPx = with(density) { SwipeThreshold.toPx() }

    // Solo il verso, non il valore: così il trascinamento non ricompone a ogni frame
    val direction by remember {
        derivedStateOf {
            when {
                offsetX.value > 0f -> 1
                offsetX.value < 0f -> -1
                else -> 0
            }
        }
    }

    Box(modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        if (direction != 0) {
            val deleting = direction > 0
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(CardRadius))
                    .background(if (deleting) SwipeDeleteBg else SwipeArchiveBg),
            ) {
                Text(
                    if (deleting) "🗑 Elimina" else "📦 Archivia",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (deleting) SwipeDeleteFg else SwipeArchiveFg,
                    modifier = Modifier
                        .align(if (deleting) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(horizontal = 22.dp),
                )
            }
        }

        Box(
            Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.placeRelative(offsetX.value.roundToInt(), 0)
                    }
                }
                .pointerInput(onSwipeArchive != null) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val settled = offsetX.value
                            scope.launch { offsetX.animateTo(0f, tween(180)) }
                            when {
                                settled > thresholdPx -> onSwipeDelete()
                                settled < -thresholdPx -> onSwipeArchive?.invoke()
                            }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(0f, tween(180)) }
                        },
                    ) { change, drag ->
                        change.consume()
                        val lowerBound = if (onSwipeArchive != null) -maxPx else 0f
                        val target = (offsetX.value + drag).coerceIn(lowerBound, maxPx)
                        scope.launch { offsetX.snapTo(target) }
                    }
                },
        ) {
            content()
        }
    }
}
