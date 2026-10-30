package cz.tejkl.klid

import android.app.Notification
import android.app.PendingIntent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NotificationItem(
    val key: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val contentIntent: PendingIntent?,
)

/** Aktuální notifikace podle balíčku, nejnovější první. */
object NotificationHub {
    private val _byPackage = MutableStateFlow<Map<String, List<NotificationItem>>>(emptyMap())
    val byPackage: StateFlow<Map<String, List<NotificationItem>>> = _byPackage.asStateFlow()

    internal fun update(map: Map<String, List<NotificationItem>>) {
        _byPackage.value = map
    }
}

class NotificationService : NotificationListenerService() {

    override fun onListenerConnected() = refresh()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    override fun onListenerDisconnected() {
        NotificationHub.update(emptyMap())
    }

    private fun refresh() {
        val active = try {
            activeNotifications ?: emptyArray()
        } catch (e: SecurityException) {
            emptyArray()
        }
        NotificationHub.update(
            active.asSequence()
                .filter { it.isClearable && !it.notification.isGroupSummary() }
                .mapNotNull { it.toItem() }
                .groupBy { it.first }
                .mapValues { (_, items) -> items.map { it.second }.sortedByDescending { it.postTime } }
        )
    }

    private fun Notification.isGroupSummary() = flags and Notification.FLAG_GROUP_SUMMARY != 0

    private fun StatusBarNotification.toItem(): Pair<String, NotificationItem>? {
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        return packageName to NotificationItem(key, title, text, postTime, notification.contentIntent)
    }
}
