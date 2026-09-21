package it.marino8383.lasttime.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.marino8383.lasttime.AppSettings
import it.marino8383.lasttime.CountersViewModel
import it.marino8383.lasttime.data.Counter
import it.marino8383.lasttime.ui.theme.OnPrimaryContainer
import it.marino8383.lasttime.ui.theme.PrimaryContainer

/**
 * Condivisione di un contatore (Milestone 9).
 *
 * Il codice si detta a voce, non si incolla: sei caratteri senza lettere ambigue,
 * perche' spesso le due persone non hanno un canale comodo per passarsi un link —
 * e fra due profili dello stesso telefono la clipboard non passa nemmeno.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    counter: Counter,
    onDismiss: () -> Unit,
    onShare: (myName: String, groupId: String?, onDone: (String?, String?) -> Unit) -> Unit,
    onGroups: ((List<CountersViewModel.GroupInfo>) -> Unit) -> Unit,
    onNewInvite: (groupId: String, onDone: (String?, String?) -> Unit) -> Unit,
    onUnshare: () -> Unit,
    onNotifyOnRemote: (Boolean) -> Unit,
    onMembers: (groupId: String, onDone: (Map<String, String>) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var myName by remember { mutableStateOf(AppSettings.myName(context)) }
    var codice by remember { mutableStateOf<String?>(null) }
    var errore by remember { mutableStateOf<String?>(null) }
    var attesa by remember { mutableStateOf(false) }
    var membri by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var gruppi by remember { mutableStateOf<List<CountersViewModel.GroupInfo>>(emptyList()) }
    var condivisoIn by remember { mutableStateOf<String?>(null) }

    val groupId = counter.sharedGroupId

    LaunchedEffect(groupId) {
        if (groupId != null) onMembers(groupId) { membri = it }
        else onGroups { gruppi = it }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            SheetTitle(title = "👥 Condividi — ${counter.name.uppercase()}", onClose = onDismiss, letterSpacing = 1.sp)
            Spacer(Modifier.height(10.dp))

            Text(
                if (groupId == null)
                    "Chi entra vede lo stesso timer: se una persona lo fa ripartire, riparte per tutti. " +
                        "Le notifiche restano una scelta di ciascuno."
                else
                    "Questo timer è condiviso. Ogni ripartenza arriva anche agli altri.",
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            if (groupId == null) {
                OutlinedTextField(
                    value = myName,
                    onValueChange = { myName = it },
                    singleLine = true,
                    label = { Text("Il tuo nome") },
                    supportingText = { Text("Serve agli altri per sapere chi ha fatto cosa") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))

                fun condividi(destinazione: String?, etichetta: String?) {
                    attesa = true
                    errore = null
                    AppSettings.setMyName(context, myName)
                    onShare(myName.trim(), destinazione) { code, err ->
                        attesa = false
                        codice = code
                        errore = err
                        if (err == null && code == null) condivisoIn = etichetta
                    }
                }

                // Gruppi che esistono gia': ci si condivide dentro senza nessun invito
                gruppi.forEach { gruppo ->
                    Button(
                        enabled = myName.isNotBlank() && !attesa,
                        onClick = { condividi(gruppo.id, gruppo.label) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Condividi con ${gruppo.label}", fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(8.dp))
                }

                Button(
                    enabled = myName.isNotBlank() && !attesa,
                    onClick = { condividi(null, null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (attesa) "Attendi..."
                        else if (gruppi.isEmpty()) "Condividi"
                        else "Con qualcun altro...",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            condivisoIn?.let { dove ->
                Spacer(Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = PrimaryContainer,
                    contentColor = OnPrimaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "✅ Condiviso nel gruppo con $dove. Nessun codice da mandare: " +
                            "ci sono già dentro.",
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            codice?.let { code ->
                Spacer(Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = PrimaryContainer,
                    contentColor = OnPrimaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("CODICE D'INVITO", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            code,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 6.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Vale 24 ore.", fontSize = 11.5.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                val cb = context.getSystemService(ClipboardManager::class.java)
                                cb.setPrimaryClip(ClipData.newPlainText("Codice Last Time", code))
                                Toast.makeText(context, "Codice copiato", Toast.LENGTH_SHORT).show()
                            }) { Text("Copia") }
                            TextButton(onClick = {
                                // Stringa multiriga: gli a capo stanno nel sorgente,
                                // niente sequenze di escape da sbagliare.
                                val testo = """
                                    Ti ho condiviso il timer “${counter.name}” su Last Time.

                                    Codice: $code

                                    Apri Last Time, ⚙️ Opzioni, “Entra con un codice” e incolla.
                                    Il codice vale 24 ore.
                                """.trimIndent()
                                val invito = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, testo)
                                }
                                context.startActivity(Intent.createChooser(invito, "Invita"))
                            }) { Text("Invia", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }

            if (groupId != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Nel gruppo: " + (if (membri.isEmpty()) "…" else membri.values.joinToString(", ")),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                TextButton(
                    enabled = !attesa,
                    onClick = {
                        attesa = true
                        errore = null
                        onNewInvite(groupId) { code, err ->
                            attesa = false
                            codice = code
                            errore = err
                        }
                    },
                ) { Text("Invita un'altra persona") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Avvisami quando lo riavvia un altro",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "Scelta tua: non riguarda gli altri del gruppo",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = counter.notifyOnRemote,
                        onCheckedChange = { onNotifyOnRemote(it) },
                    )
                }
                Spacer(Modifier.height(14.dp))

                Text(
                    "Smettendo di condividere il timer esce dal gruppo per tutti, e ogni " +
                        "telefono se lo tiene com'è, con il suo storico, per conto proprio.",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = {
                    onUnshare()
                    onDismiss()
                }) {
                    Text("Smetti di condividere", color = MaterialTheme.colorScheme.error)
                }
            }

            errore?.let {
                Spacer(Modifier.height(10.dp))
                Text("⚠️ $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
