package it.marino8383.lasttime.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit

/**
 * Intestazione unica di maschere e dialoghi: titolo a sinistra, ✕ a destra.
 *
 * Ogni maschera che si apre deve potersi chiudere senza fare nulla. Lo swipe verso il
 * basso e il tap fuori funzionavano già, ma non si vedono: la ✕ rende la via d'uscita
 * esplicita, ed è la stessa dappertutto così non c'è da ricordarsi quale maschera ce
 * l'ha e quale no.
 */
@Composable
fun SheetTitle(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    letterSpacing: TextUnit = TextUnit.Unspecified,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = style,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = letterSpacing,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClose) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Chiudi senza salvare",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
