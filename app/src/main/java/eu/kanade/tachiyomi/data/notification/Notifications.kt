package eu.kanade.tachiyomi.data.notification

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_DEFAULT
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_HIGH
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_LOW
import eu.kanade.tachiyomi.util.system.buildNotificationChannel
import eu.kanade.tachiyomi.util.system.buildNotificationChannelGroup
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR

/**
 * Class to manage the basic information of all the notifications used in the app.
 */
object Notifications {

    /**
     * Common notification channel and ids used anywhere.
     */
    const val CHANNEL_COMMON = "common_channel"
    const val ID_DOWNLOAD_IMAGE = 2

    /**
     * Notification channel and ids used by the downloader.
     */
    private const val GROUP_DOWNLOADER = "group_downloader"
    const val CHANNEL_DOWNLOADER_PROGRESS = "downloader_progress_channel"
    const val ID_DOWNLOAD_CHAPTER_PROGRESS = -201
    const val ID_DOWNLOAD_EPISODE_PROGRESS = -203
    const val CHANNEL_DOWNLOADER_ERROR = "downloader_error_channel"
    const val ID_DOWNLOAD_CHAPTER_ERROR = -202
    const val ID_DOWNLOAD_EPISODE_ERROR = -204

    /**
     * Notification channel used for Incognito Mode
     */
    const val CHANNEL_INCOGNITO_MODE = "incognito_mode_channel"
    const val ID_INCOGNITO_MODE = -701


    /**
     * Notification channel and ids used for http server
     */
    const val CHANNEL_HTTP_SERVER = "http_server_channel"
    const val ID_HTTP_SERVER = -901

    /**
     * Notification channel and ids used for app and extension updates.
     */
    private const val GROUP_APK_UPDATES = "group_apk_updates"
    const val CHANNEL_EXTENSIONS_UPDATE = "ext_apk_update_channel"
    const val ID_UPDATES_TO_EXTS = -401
    const val ID_EXTENSION_INSTALLER = -402

    private val deprecatedChannels = listOf(
        "downloader_channel",
        "downloader_complete_channel",
        "backup_restore_complete_channel",
        "library_channel",
        "library_progress_channel",
        "updates_ext_channel",
        "downloader_cache_renewal",
        "crash_logs_channel",
        "library_skipped_channel",
    )

    /**
     * Creates the notification channels introduced in Android Oreo.
     * This won't do anything on Android versions that don't support notification channels.
     *
     * @param context The application context.
     */
    fun createChannels(context: Context) {
        val notificationManager = NotificationManagerCompat.from(context)

        // Delete old notification channels
        deprecatedChannels.forEach(notificationManager::deleteNotificationChannel)

        notificationManager.createNotificationChannelGroupsCompat(
            listOf(
                buildNotificationChannelGroup(GROUP_DOWNLOADER) {
                    setName(context.stringResource(MR.strings.download_notifier_downloader_title))
                },
                buildNotificationChannelGroup(GROUP_APK_UPDATES) {
                    setName(context.stringResource(MR.strings.label_recent_updates))
                },
            ),
        )

        notificationManager.createNotificationChannelsCompat(
            listOf(
                buildNotificationChannel(CHANNEL_COMMON, IMPORTANCE_LOW) {
                    setName(context.stringResource(MR.strings.channel_common))
                },
                buildNotificationChannel(CHANNEL_DOWNLOADER_PROGRESS, IMPORTANCE_LOW) {
                    setName(context.stringResource(MR.strings.channel_progress))
                    setGroup(GROUP_DOWNLOADER)
                    setShowBadge(false)
                },
                buildNotificationChannel(CHANNEL_DOWNLOADER_ERROR, IMPORTANCE_LOW) {
                    setName(context.stringResource(MR.strings.channel_errors))
                    setGroup(GROUP_DOWNLOADER)
                    setShowBadge(false)
                },
                buildNotificationChannel(CHANNEL_INCOGNITO_MODE, IMPORTANCE_LOW) {
                    setName(context.stringResource(MR.strings.pref_incognito_mode))
                },
                buildNotificationChannel(CHANNEL_HTTP_SERVER, IMPORTANCE_LOW) {
                    setName(context.stringResource(AYMR.strings.pref_http_server_name))
                    setShowBadge(false)
                },
                buildNotificationChannel(CHANNEL_EXTENSIONS_UPDATE, IMPORTANCE_DEFAULT) {
                    setGroup(GROUP_APK_UPDATES)
                    setName(context.stringResource(MR.strings.channel_ext_updates))
                },
            ),
        )
    }
}
