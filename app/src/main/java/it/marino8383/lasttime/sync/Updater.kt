package it.marino8383.lasttime.sync

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import it.marino8383.lasttime.AppSettings
import it.marino8383.lasttime.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Controllo aggiornamenti.
 *
 * La CI, a ogni build verde, pubblica una release con dentro l'APK e un `latest.json`.
 * L'indirizzo `releases/latest/download/...` punta sempre all'ultima, quindi qui non
 * serve conoscere nessuna versione in anticipo: si legge un file da poche centinaia di
 * byte e si confronta un numero.
 *
 * L'app **non installa niente da sola**: scarica e passa l'APK all'installer di Android,
 * che chiede conferma. Serve anche che l'utente abbia autorizzato "installa app
 * sconosciute" per Last Time, una volta sola.
 */
object Updater {

    private const val TAG = "LastTimeUpdater"
    private const val REPO = "marino8383/lasttime"

    const val PAGINA_RELEASE = "https://github.com/$REPO/releases/latest"
    private const val MANIFEST = "https://github.com/$REPO/releases/latest/download/latest.json"

    /** Un controllo al giorno basta: una versione nuova non esce quattro volte all'ora. */
    private const val INTERVALLO_MS = 24 * 60 * 60 * 1000L

    data class Novita(val versionCode: Int, val versionName: String, val apk: String)

    /**
     * Ritorna la versione nuova se ce n'è una, altrimenti null. Con [forzato] a false
     * salta del tutto se è già stato fatto un controllo nelle ultime 24 ore.
     */
    suspend fun controlla(context: Context, forzato: Boolean = false): Novita? =
        withContext(Dispatchers.IO) {
            val ora = System.currentTimeMillis()
            if (!forzato && ora - AppSettings.lastUpdateCheckMs(context) < INTERVALLO_MS) {
                return@withContext null
            }
            try {
                val testo = scarica(MANIFEST) ?: return@withContext null
                AppSettings.setLastUpdateCheckMs(context, ora)
                val j = JSONObject(testo)
                val code = j.getInt("versionCode")
                if (code <= BuildConfig.VERSION_CODE) return@withContext null
                Novita(code, j.getString("versionName"), j.getString("apk"))
            } catch (t: Throwable) {
                Log.w(TAG, "controllo aggiornamenti fallito", t)
                null
            }
        }

    private fun scarica(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            instanceFollowRedirects = true
        }
        return try {
            if (conn.responseCode != 200) null else conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Scarica l'APK con il DownloadManager di sistema — gestisce da sé rete che va e
     * viene, notifica di avanzamento e ripresa — e a fine scaricamento apre l'installer.
     */
    fun scaricaEInstalla(context: Context, novita: Novita) {
        val app = context.applicationContext
        val cartella = File(app.getExternalFilesDir(null), "updates")
        cartella.mkdirs()
        val nome = "lasttime-${novita.versionName}.apk"
        val destinazione = File(cartella, nome)
        destinazione.delete()

        val richiesta = DownloadManager.Request(Uri.parse(novita.apk))
            .setTitle("Last Time ${novita.versionName}")
            .setDescription("Scaricamento dell'aggiornamento")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(app, null, "updates/$nome")
            .setMimeType("application/vnd.android.package-archive")

        val dm = app.getSystemService(DownloadManager::class.java)
        val id = dm.enqueue(richiesta)

        // A scaricamento finito si apre l'installer da soli: la notifica del
        // DownloadManager punta a un file nello spazio privato dell'app, e toccandola
        // spesso non succede niente. Meglio non far dipendere l'ultimo passo da quello.
        val ricevitore = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != id) return
                runCatching { app.unregisterReceiver(this) }
                if (destinazione.exists()) installa(app, destinazione)
            }
        }
        ContextCompat.registerReceiver(
            app,
            ricevitore,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    /** Apre l'installer di sistema su un APK già scaricato. */
    fun installa(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
