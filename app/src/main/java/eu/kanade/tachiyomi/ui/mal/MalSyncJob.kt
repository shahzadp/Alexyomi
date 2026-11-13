package eu.kanade.tachiyomi.ui.mal

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.collections.immutable.toImmutableList
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/**
 * Keeps the watched episodes of the app and the MyAnimeList list in sync.
 *
 * It runs in the background so the list of the user is not fetched while it is opened:
 *
 * 1. the list of the user is fetched and the missing details are filled in
 * 2. the list entries are linked with the local anime and the progress is applied
 * 3. the anime that have no episodes yet get their episode list fetched, so their progress
 *    can be ticked as well
 */
class MalSyncJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        if (runAttemptCount > 3) return Result.failure()

        val tracker = Injekt.get<TrackerManager>().myAnimeList
        if (!tracker.isLoggedIn) return Result.success()

        return try {
            withIOContext {
                for (mediaType in MalListScreenModel.MediaType.entries) {
                    val entries = MalListSync.fetch(mediaType).toMutableList()
                    MalListSync.enrich(entries, mediaType)
                    val complete = entries.toImmutableList()
                    MalListStore.write(mediaType, complete)

                    when (mediaType) {
                        MalListScreenModel.MediaType.ANIME -> MalProgressSync.applyListProgress(complete)
                        MalListScreenModel.MediaType.MANGA -> MalProgressSync.applyMangaListProgress(complete)
                    }
                }

                // The anime that are on the list but have no episodes yet need them, otherwise
                // there is nothing to tick.
                MalProgressSync.fetchMissingEpisodes()
            }
            Result.success()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "MyAnimeList sync failed" }
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "MalSync"

        /**
         * Plans the sync.
         *
         * The first run waits a bit - the app is used right after it was opened, and the sync
         * should not compete with that.
         */
        fun setupTask(context: Context) {
            val constraints = Constraints(
                requiredNetworkType = NetworkType.CONNECTED,
            )

            val request = PeriodicWorkRequestBuilder<MalSyncJob>(SYNC_INTERVAL.toJavaDuration())
                .setConstraints(constraints)
                .setInitialDelay(INITIAL_DELAY.toJavaDuration())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5.minutes.toJavaDuration())
                .addTag(TAG)
                .build()

            context.workManager.enqueueUniquePeriodicWork(
                TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /**
         * Runs the sync now - used when the user refreshes the list by hand.
         */
        fun runNow(context: Context) {
            val constraints = Constraints(
                requiredNetworkType = NetworkType.CONNECTED,
            )

            val request = OneTimeWorkRequestBuilder<MalSyncJob>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5.minutes.toJavaDuration())
                .addTag(TAG)
                .build()

            context.workManager.enqueueUniqueWork("$TAG-now", ExistingWorkPolicy.KEEP, request)
        }

        /** How often the list of the user is synced. */
        private val SYNC_INTERVAL = 6.hours

        /** The first sync waits, so it doesn't slow the app down right after it was opened. */
        private val INITIAL_DELAY = 10.minutes
    }
}
