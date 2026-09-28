package it.marino8383.lasttime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.marino8383.lasttime.data.quickStartTimes
import it.marino8383.lasttime.formatClock
import it.marino8383.lasttime.formatDurationTwoParts

/**
 * "Adesso" con la scelta rapida di un orario tondo, per un timer nuovo.
 *
 * Tocco semplice = adesso, al secondo. Tenendo premuto compare sotto la fila degli
 * orari vicini (alle 14:19: 14:00 · 14:15 · 14:19 · 14:20 · 14:30 · 15:00): si scorre col
 * dito e si rilascia su quello voluto. La fila resta aperta, così si può anche toccare.
 * Un orario nel futuro fa partire il timer più tardi, da solo.
 *
 * [pickedMs] null = adesso preciso; [onPick] riceve null per "adesso" o l'orario scelto.
 */
@Composable
fun QuickStartPick(
    selected: Boolean,
    pickedMs: Long?,
    onPick: (Long?) -> Unit,
) {
    val pick by rememberUpdatedState(onPick)
    var open by remember { mutableStateOf(false) }
    // istante di riferimento della fila: fissato quando la si apre, non scorre sotto il dito
    var nowAtOpen by remember { mutableStateOf(0L) }
    var hovered by remember { mutableStateOf<Int?>(null) }
    val itemBounds = remember { mutableStateMapOf<Int, Rect>() }
    var chipCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val times = if (open) quickStartTimes(nowAtOpen) else emptyList()

    fun indexAt(local: Offset): Int? {
        val root = chipCoords?.localToRoot(local) ?: return null
        return itemBounds.entries.firstOrNull { (_, r) ->
            // tolleranza verticale larga: il dito copre la casella, non serve centrarla
            root.x in r.left..r.right && root.y in (r.top - r.height)..(r.bottom + r.height * 2)
        }?.key
    }

    fun choose(i: Int) {
        // ricalcolata da nowAtOpen e non presa da `times`: il gestore del gesto nasce alla
        // prima composizione e lì `times` era ancora vuota
        val t = quickStartTimes(nowAtOpen).getOrNull(i) ?: return
        pick(if (t == nowAtOpen) null else t)
    }

    val attivo = selected
    val chipLabel = when {
        pickedMs == null -> "Adesso"
        pickedMs > System.currentTimeMillis() -> "⏲ ${formatClock(pickedMs)}"
        else -> formatClock(pickedMs)
    }

    Column {
        Box(
            Modifier
                .onGloballyPositioned { chipCoords = it }
                .clip(RoundedCornerShape(8.dp))
                .background(if (attivo) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                .border(1.dp, if (attivo) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var up: PointerInputChange? = null
                        val finitoPrima = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            up = waitForUpOrCancellation()
                            true
                        } ?: false
                        if (finitoPrima) {
                            // tocco semplice: adesso, al secondo
                            if (up != null) {
                                open = false
                                pick(null)
                            }
                            return@awaitEachGesture
                        }
                        // tenuto premuto: si apre la fila e si segue il dito
                        nowAtOpen = System.currentTimeMillis()
                        itemBounds.clear()
                        open = true
                        var pos = down.position
                        drag(down.id) { change ->
                            pos = change.position
                            hovered = indexAt(pos)
                            change.consume()
                        }
                        indexAt(pos)?.let { choose(it) }
                        hovered = null
                    }
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(chipLabel, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }

        if (open) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                times.forEachIndexed { i, t ->
                    val scelto = if (pickedMs == null) t == nowAtOpen else t == pickedMs
                    val futuro = t > nowAtOpen
                    Box(
                        Modifier
                            .weight(1f)
                            .onGloballyPositioned { itemBounds[i] = it.boundsInRoot() }
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    hovered == i -> MaterialTheme.colorScheme.primary
                                    scelto -> MaterialTheme.colorScheme.secondaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                }
                            )
                            .clickable { choose(i) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                formatClock(t),
                                fontSize = 13.sp,
                                fontWeight = if (t == nowAtOpen) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (hovered == i) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                when {
                                    t == nowAtOpen -> "adesso"
                                    futuro -> "dopo"
                                    else -> "prima"
                                },
                                fontSize = 9.sp,
                                color = if (hovered == i) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        val msg = when {
            pickedMs != null && pickedMs > System.currentTimeMillis() ->
                "⏲ Parte alle ${formatClock(pickedMs)}, fra ${formatDurationTwoParts(pickedMs - System.currentTimeMillis())}: fino ad allora resta in attesa."
            pickedMs != null -> "Inizio alle ${formatClock(pickedMs)}."
            selected && !open -> "Tieni premuto per un orario tondo."
            else -> null
        }
        msg?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
