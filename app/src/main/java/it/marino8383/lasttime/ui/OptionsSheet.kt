package it.marino8383.lasttime.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.marino8383.lasttime.AppSettings
import it.marino8383.lasttime.BuildConfig
import it.marino8383.lasttime.sync.Updater
import it.marino8383.lasttime.ui.theme.OnPrimaryContainer
import it.marino8383.lasttime.ui.theme.PrimaryContainer
import kotlinx.coroutines.launch

/** Pannello Opzioni (⚙️): per ora la tolleranza "mantieni il ritmo". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsSheet(
    onDismiss: () -> Unit,
    onJoin: (code: String, myName: String, onDone: (String) -> Unit) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    var percentText by remember {
        mutableStateOf(AppSettings.latePercent(context).toString())
    }
    var codice by remember { mutableStateOf("") }
    var mioNome by remember { mutableStateOf(AppSettings.myName(context)) }
    var esito by remember { mutableStateOf<String?>(null) }
    var attesa by remember { mutableStateOf(false) }
    var controlloVersione by remember { mutableStateOf(false) }
    var esitoVersione by remember { mutableStateOf<Updater.Esito?>(null) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
        ) {
            SheetTitle(title = "⚙️ Opzioni", onClose = onDismiss)
            Spacer(Modifier.height(16.dp))

            Text(
                "TOLLERANZA “MANTIENI IL RITMO”",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = percentText,
                onValueChange = { txt ->
                    percentText = txt.filter(Char::isDigit).take(2)
                    percentText.toIntOrNull()?.let { AppSettings.setLatePercent(context, it) }
                },
                label = { Text("Percentuale del periodo (1–50)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                suffix = { Text("%") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Se fai Fatto/↺ entro questo ritardo dopo lo squillo di una ricorrente, " +
                    "la campanella mantiene il ritmo senza chiedere. Oltre, appare la scelta. " +
                    "Es. 3% di 8 h ≈ 15 min; 3% di 1 min ≈ 2 s.",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(26.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(18.dp))

            Text(
                "INSTALLAZIONE",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Hai la versione ${BuildConfig.VERSION_NAME}.",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Button(
                enabled = !controlloVersione,
                onClick = {
                    controlloVersione = true
                    esitoVersione = null
                    scope.launch {
                        esitoVersione = Updater.controllaOra(context)
                        controlloVersione = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (controlloVersione) "Controllo..." else "🔄 Controlla aggiornamenti",
                    fontWeight = FontWeight.Bold,
                )
            }

            when (val esito = esitoVersione) {
                is Updater.Esito.Aggiornato -> {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "✅ Hai già la versione più recente.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                is Updater.Esito.Fallito -> {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "⚠️ Non sono riuscito a controllare — verifica la connessione e riprova.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                is Updater.Esito.Disponibile -> {
                    val nuova = esito.novita
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = PrimaryContainer,
                        contentColor = OnPrimaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "🆕 Disponibile la versione ${nuova.versionName}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            if (nuova.notes.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(nuova.notes, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = { Updater.scaricaEInstalla(context, nuova) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("⬇️ Scarica e installa", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                null -> {}
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "Il link punta sempre all'ultima versione: chi lo apre trova l'APK più " +
                    "recente, oggi e fra sei mesi.",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    val testo = """
                        Last Time — l'app per sapere da quanto tempo non succede qualcosa.

                        Si scarica da qui: ${Updater.PAGINA_RELEASE}

                        È un APK: al primo avvio Android chiederà di autorizzare
                        l'installazione da questa origine.
                    """.trimIndent()
                    val invito = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, testo)
                    }
                    context.startActivity(Intent.createChooser(invito, "Condividi"))
                }) { Text("Condividi il link", fontWeight = FontWeight.Bold) }
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(Updater.PAGINA_RELEASE))
                    )
                }) { Text("Apri la pagina") }
            }

            Spacer(Modifier.height(22.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(18.dp))

            Text(
                "TIMER CONDIVISI",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Se qualcuno ti ha dettato un codice, mettilo qui: i timer che condivide " +
                    "compariranno fra i tuoi.",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = codice,
                onValueChange = { codice = it.uppercase().filter(Char::isLetterOrDigit).take(6) },
                label = { Text("Codice d'invito") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = mioNome,
                onValueChange = { mioNome = it },
                label = { Text("Il tuo nome") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                enabled = codice.length == 6 && mioNome.isNotBlank() && !attesa,
                onClick = {
                    attesa = true
                    esito = null
                    AppSettings.setMyName(context, mioNome)
                    onJoin(codice, mioNome.trim()) { messaggio ->
                        attesa = false
                        esito = messaggio
                        if (messaggio.startsWith("✅")) codice = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (attesa) "Attendi..." else "Entra con un codice", fontWeight = FontWeight.Bold) }

            esito?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
