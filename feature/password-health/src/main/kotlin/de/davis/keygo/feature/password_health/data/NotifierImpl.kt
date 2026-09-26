package de.davis.keygo.feature.password_health.data

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.davis.keygo.feature.password_health.R
import de.davis.keygo.feature.password_health.domain.Notifier
import de.davis.keygo.feature.password_health.domain.model.KeyGoNotification
import de.davis.keygo.feature.password_health.domain.model.isEmpty
import org.koin.core.annotation.Single
import de.davis.keygo.core.ui.R as CoreUiR

@Single
internal class NotifierImpl(
    private val context: Context,
) : Notifier {

    private val notificationManagerCompat by lazy { NotificationManagerCompat.from(context) }

    override fun canNotify(): Boolean {
        val permissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        else true

        return permissionGranted && notificationManagerCompat.areNotificationsEnabled()
    }

    @Suppress("MissingPermission")
    override fun sendNotification(notification: KeyGoNotification) {
        if (notification.isEmpty) {
            notificationManagerCompat.cancel(notification.id)
            return
        }
        if (!canNotify()) return

        createChannels()
        notificationManagerCompat.notify(notification.id, notification.buildNotification())
    }

    private fun createChannels() {
        val channels = Channel.entries.map {
            NotificationChannelCompat.Builder(it.id, it.importance)
                .setName(context.getString(it.nameRes))
                .setDescription(context.getString(it.descriptionRes))
                .build()
        }
        notificationManagerCompat.createNotificationChannelsCompat(channels)
    }

    private fun KeyGoNotification.buildNotification(): Notification = when (this) {
        is KeyGoNotification.NeedsAttention -> baseNotification()
            .setContentText(
                context.resources.getQuantityString(
                    R.plurals.password_health_needs_attention,
                    count,
                    count,
                ),
            )
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(baseNotification().build())
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
    }

    private fun KeyGoNotification.baseNotification() =
        NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(CoreUiR.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.password_health_title))

    // TODO: send user to correct screen
    private fun openAppIntent(): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private val KeyGoNotification.channel: Channel
        get() = when (this) {
            is KeyGoNotification.NeedsAttention -> Channel.PasswordHealth
        }

    private val KeyGoNotification.id: Int
        get() = when (this) {
            is KeyGoNotification.NeedsAttention -> 1001
        }

    private enum class Channel(
        val id: String,
        val importance: Int,
        @param:StringRes val nameRes: Int,
        @param:StringRes val descriptionRes: Int,
    ) {
        PasswordHealth(
            id = "password_health_channel",
            importance = NotificationManagerCompat.IMPORTANCE_LOW,
            nameRes = R.string.password_health_notification_channel_name,
            descriptionRes = R.string.password_health_notification_channel_description,
        ),
    }
}
