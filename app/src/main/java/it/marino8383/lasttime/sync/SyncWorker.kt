package it.marino8383.lasttime.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import it.marino8383.lasttime.LastTimeApp
import java.util.concurrent.TimeUnit

/**
 * Allineamento periodico dei timer condivisi.
 *
 * Serve al caso "app chiusa": senza, la ripartenza fatta dall'altra persona arriverebbe
 * solo alla prossima apertura, e fino a quel momento la campanella suonerebbe sull'orario
 * vecchio. Con questo, il ritardo massimo diventa il quarto d'ora fra due giri.
 *
 * Non sveglia il telefono: WorkManager accoda il lavoro e lo fa girare nelle finestre di
 * manutenzione del sistema, insieme a quello delle altre app. Se non c'e' rete non parte
 * affatto, e se non condividi niente non viene nemmeno programmato.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as LastTimeApp
        return if (SyncEngine.syncOnce(app)) Result.success() else Result.retry()
    }

    companion object {
        private const val NAME = "sync-condivisi"
        private const val MINUTI = 15L

        /**
         * Accende o spegne il giro periodico a seconda che ci sia qualcosa da condividere.
         * Da chiamare quando si condivide, si smette di condividere o si entra in un gruppo.
         */
        suspend fun refresh(context: Context) {
            val app = context.applicationContext as LastTimeApp
            val serve = app.db.counterDao().shared().isNotEmpty()
            val wm = WorkManager.getInstance(app)
            if (!serve) {
                wm.cancelUniqueWork(NAME)
                return
            }
            val richiesta = PeriodicWorkRequestBuilder<SyncWorker>(MINUTI, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            // KEEP: se il giro e' gia' programmato non lo si riavvia da capo a ogni modifica,
            // altrimenti il conto dei 15 minuti ripartirebbe di continuo e non scatterebbe mai.
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, richiesta)
        }
    }
}
