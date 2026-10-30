package cz.tejkl.klid

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract

data class CalendarEvent(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
)

data class Contact(val id: Long, val lookupKey: String, val name: String) {
    val uri: Uri get() = ContactsContract.Contacts.getLookupUri(id, lookupKey)
}

private fun Context.granted(permission: String) =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

/** Nejbližší události v příštích 24 hodinách. */
fun upcomingEvents(context: Context, limit: Int = 3): List<CalendarEvent> {
    if (!context.granted(Manifest.permission.READ_CALENDAR)) return emptyList()
    val now = System.currentTimeMillis()
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
        ContentUris.appendId(it, now)
        ContentUris.appendId(it, now + 24 * 60 * 60 * 1000L)
    }.build()
    val projection = arrayOf(
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY,
    )
    return try {
        context.contentResolver.query(
            uri, projection, "${CalendarContract.Instances.VISIBLE} = 1", null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val event = CalendarEvent(c.getLong(0), c.getString(1).orEmpty(), c.getLong(2), c.getLong(3), c.getInt(4) == 1)
                    if (event.end > now && event.title.isNotBlank()) add(event)
                }
            }.take(limit)
        } ?: emptyList()
    } catch (_: SecurityException) {
        emptyList()
    }
}

fun searchContacts(context: Context, query: String, limit: Int = 4): List<Contact> {
    if (query.isBlank() || !context.granted(Manifest.permission.READ_CONTACTS)) return emptyList()
    val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query.trim()))
    val projection = arrayOf(
        ContactsContract.Contacts._ID,
        ContactsContract.Contacts.LOOKUP_KEY,
        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
    )
    return try {
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            buildList {
                while (c.moveToNext() && size < limit) add(Contact(c.getLong(0), c.getString(1), c.getString(2).orEmpty()))
            }
        } ?: emptyList()
    } catch (_: SecurityException) {
        emptyList()
    }
}
