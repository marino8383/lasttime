package it.marino8383.lasttime.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import it.marino8383.lasttime.ui.theme.SwipeArchiveBg
import it.marino8383.lasttime.ui.theme.SwipeArchiveFg
import it.marino8383.lasttime.ui.theme.SwipeDeleteBg
import it.marino8383.lasttime.ui.theme.SwipeDeleteFg
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val SwipeMaxRight = 150.dp
private val SwipeThresholdRight = 90.dp

// A sinistra, quando c'è anche "nascondi": due riquadri, uno sopra l'altro, da toccare
// invece di un trascinamento a soglia — si apre come un cassetto (si ferma tutto
// aperto o tutto chiuso, mai a metà) e si sceglie toccando quello giusto. Si apre già
// dopo un trascinamento corto (SwipeOpenTrigger), ma scivola via la card per intero
// (larghezza vera, misurata) — altrimenti, essendo la card più larga della sola zona
// rivelata, resterebbe visibile a metà uno spicchio tagliato di testo e icone.
private val SwipeOpenTrigger = 90.dp

private val CardRadius = 26.dp

/**
 * Card trascinabile in orizzontale (v23): swipe a destra = elimina (soglia, come
 * sempre). Swipe a sinistra: se [onSwipeHide] è null, si comporta come prima (soglia =
 * archivia); se non è null, si apre un cassetto con due riquadri, uno sopra l'altro —
 * [hideLabel] in alto e "Ferma e archivia" sotto — e si sceglie toccando quello voluto (v0.22:
 * nascondere è troppo frequente e leggero per condividere lo stesso gesto-soglia di
 * archivia/elimina).
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
    onSwipeHide: (() -> Unit)? = null,
    hideLabel: String = "Nascondi",
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current
    val maxRightPx = with(density) { SwipeMaxRight.toPx() }
    val thresholdRightPx = with(density) { SwipeThresholdRight.toPx() }
    val openTriggerPx = with(density) { SwipeOpenTrigger.toPx() }
    val dueRiquadri = onSwipeHide != null
    // Larghezza vera della card, misurata: prima che sia nota (primo frame) usa il
    // valore di sempre come fallback, così non si divide per zero.
    var cardWidthPx by remember { mutableFloatStateOf(maxRightPx) }

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

    fun animaA(target: Float) {
        scope.launch { offsetX.animateTo(target, tween(180)) }
    }

    // pointerInput riparte solo quando cambia la chiave (qui due booleani): senza questi, il
    // gesto continuerebbe a chiamare le lambda della PRIMA composizione, cioe' a consegnare il
    // timer com'era allora. Era la causa del timer condiviso "eliminato" che tornava: se era
    // stato condiviso dopo che la card era a schermo, l'eliminazione lo vedeva ancora non
    // condiviso, lo toglieva solo da questo telefono e lasciava vivo il suo documento nel gruppo.
    val eliminaOra by rememberUpdatedState(onSwipeDelete)
    val archiviaOra by rememberUpdatedState(onSwipeArchive)

    Box(
        modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .onSizeChanged { cardWidthPx = it.width.toFloat() },
    ) {
        if (direction > 0) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(CardRadius))
                    .background(SwipeDeleteBg),
            ) {
                Text(
                    "🗑 Elimina",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SwipeDeleteFg,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(horizontal = 22.dp),
                )
            }
        } else if (direction < 0) {
            if (dueRiquadri) {
                Column(
                    Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(CardRadius)),
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable {
                                onSwipeHide?.invoke()
                                animaA(0f)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            hideLabel,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(SwipeArchiveBg)
                            .clickable {
                                onSwipeArchive?.invoke()
                                animaA(0f)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Ferma e archivia",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = SwipeArchiveFg,
                        )
                    }
                }
            } else {
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(CardRadius))
                        .background(SwipeArchiveBg),
                ) {
                    Text(
                        "⏹ Ferma e archivia",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SwipeArchiveFg,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(horizontal = 22.dp),
                    )
                }
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
                .pointerInput(onSwipeArchive != null, dueRiquadri) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val settled = offsetX.value
                            when {
                                settled > thresholdRightPx -> {
                                    animaA(0f)
                                    eliminaOra()
                                }
                                dueRiquadri && settled < -openTriggerPx -> animaA(-cardWidthPx)
                                dueRiquadri -> animaA(0f)
                                archiviaOra != null && settled < -thresholdRightPx -> {
                                    animaA(0f)
                                    archiviaOra?.invoke()
                                }
                                else -> animaA(0f)
                            }
                        },
                        onDragCancel = { animaA(0f) },
                    ) { change, drag ->
                        change.consume()
                        val lowerBound = when {
                            dueRiquadri -> -cardWidthPx
                            onSwipeArchive != null -> -maxRightPx
                            else -> 0f
                        }
                        val target = (offsetX.value + drag).coerceIn(lowerBound, maxRightPx)
                        scope.launch { offsetX.snapTo(target) }
                    }
                },
        ) {
            content()
        }
    }
}
