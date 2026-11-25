package it.luxorshade.owtrend

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Controlla ogni qualche ora se nel repository c'è un report nuovo
 * e, se sì, manda una notifica.
 */
class ReportCheckWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = Repository(applicationContext)
        if (!repo.notificationsEnabled) return Result.success()
        return try {
            val latest = repo.latestSummary() ?: return Result.success()
            if (latest.id != repo.lastSeenId) {
                // Alla prima esecuzione non notifichiamo i report già esistenti
                if (repo.lastSeenId.isNotEmpty()) notify(latest)
                repo.lastSeenId = latest.id
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun notify(summary: ReportSummary) {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        ensureChannel(ctx)
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("Nuovo report: ${summary.week}")
            .setContentText(summary.summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary.summary))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(1001, notification)
    }

    companion object {
        private const val CHANNEL_ID = "report_settimanale"
        private const val WORK_NAME = "controllo_report"

        fun ensureChannel(ctx: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Report settimanale", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Avviso quando esce un nuovo report" }
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        fun schedule(ctx: Context) {
            val request = PeriodicWorkRequestBuilder<ReportCheckWorker>(4, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
