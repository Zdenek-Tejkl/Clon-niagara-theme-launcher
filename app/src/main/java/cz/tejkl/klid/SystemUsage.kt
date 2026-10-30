package cz.tejkl.klid

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process

/** Počty otevření aplikací ze systémové statistiky využití. */
object SystemUsage {
    private const val DAY = 24 * 60 * 60 * 1000L

    fun hasAccess(context: Context): Boolean {
        val mode = context.getSystemService(AppOpsManager::class.java)
            .unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED ||
            (mode == AppOpsManager.MODE_DEFAULT &&
                context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED)
    }

    /** Balíček -> kolikrát byl otevřen za posledních [days] dní. */
    fun openCounts(context: Context, days: Int = 14): Map<String, Int> {
        if (!hasAccess(context)) return emptyMap()
        val end = System.currentTimeMillis()
        val events = context.getSystemService(UsageStatsManager::class.java)
            .queryEvents(end - days * DAY, end) ?: return emptyMap()
        val counts = HashMap<String, Int>()
        val event = UsageEvents.Event()
        var lastPackage: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
            // Přechod mezi obrazovkami jedné aplikace nepočítáme jako nové otevření.
            if (event.packageName != lastPackage) counts.merge(event.packageName, 1, Int::plus)
            lastPackage = event.packageName
        }
        return counts
    }
}
