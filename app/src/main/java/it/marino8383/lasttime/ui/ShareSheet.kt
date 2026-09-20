package it.marino8383.lasttime.ui

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.marino8383.lasttime.AppSettings
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
    onShare: (myName: String, onDone: (String?, String?) -> Unit) -> Unit,
    onNewInvite: (groupId: String, onDone: (String?, String?) -> Unit) -> Unit,
    onUnshare: () -> Unit,
    onMembers: (groupId: String, onDone: (Map<String, String>) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var myName by remember { mutableStateOf(AppSettings.myName(context)) }
    var codice by remember { mutableStateOf<String?>(null) }
    var errore by remember { mutableStateOf<String?>(null) }
    var attesa by remember { mutableStateOf(false) }
    var membri by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val groupId = counter.sharedGroupId

    LaunchedEffect(groupId) {
        if (groupId != null) onMembers(groupId) { membri = it }
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
                Button(
                    enabled = myName.isNotBlank() && !attesa,
                    onClick = {
                        attesa = true
                        errore = null
                        AppSettings.setMyName(context, myName)
                        onShare(myName.trim()) { code, err ->
                            attesa = false
                            codice = code
                            errore = err
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (attesa) "Attendi..." else "Condividi", fontWeight = FontWeight.Bold) }
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
                        Text(
                            "Vale 24 ore. Dettalo all'altra persona: apre Last Time, " +
                                "⚙️ Opzioni, “Entra con un codice”.",
                            fontSize = 11.5.sp,
                        )
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
