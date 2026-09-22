package it.marino8383.lasttime.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.marino8383.lasttime.AppSettings

/**
 * Aperto quando si tocca il link d'invito ricevuto (https://.../lasttime/join?code=...):
 * conferma esplicita prima di entrare nel gruppo, non un ingresso silenzioso.
 */
@Composable
fun JoinInviteDialog(
    code: String,
    onDismiss: () -> Unit,
    onJoin: (myName: String, onDone: (String) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var myName by remember { mutableStateOf(AppSettings.myName(context)) }
    var attesa by remember { mutableStateOf(false) }
    var esito by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            SheetTitle(
                title = "👥 Invito ricevuto",
                onClose = onDismiss,
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column {
                Text(
                    "Qualcuno ti ha mandato un link per un timer condiviso. Vuoi entrare nel gruppo?",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Codice: $code",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = myName,
                    onValueChange = { myName = it },
                    singleLine = true,
                    label = { Text("Il tuo nome") },
                    supportingText = { Text("Serve agli altri per sapere chi ha fatto cosa") },
                    modifier = Modifier.fillMaxWidth(),
                )
                esito?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = myName.isNotBlank() && !attesa,
                onClick = {
                    attesa = true
                    esito = null
                    AppSettings.setMyName(context, myName)
                    onJoin(myName.trim()) { messaggio ->
                        attesa = false
                        esito = messaggio
                        if (messaggio.startsWith("✅")) onDismiss()
                    }
                },
            ) { Text(if (attesa) "Attendi..." else "Entra nel gruppo") }
        },
        dismissButton = {},
    )
}
