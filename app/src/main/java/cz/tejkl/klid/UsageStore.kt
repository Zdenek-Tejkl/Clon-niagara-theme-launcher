package cz.tejkl.klid

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Skóre používání aplikací. Při každém spuštění se staré skóre mírně ztlumí,
 * takže návrhy sledují, co používáš poslední dobou.
 */
class UsageStore(context: Context) {
    private val prefs = context.getSharedPreferences("klid_usage", Context.MODE_PRIVATE)
    private val _scores = MutableStateFlow(load())
    val scores: StateFlow<Map<String, Float>> = _scores.asStateFlow()

    private fun load(): Map<String, Float> =
        prefs.all.mapNotNull { (k, v) -> (v as? Float)?.let { k to it } }.toMap()

    fun recordLaunch(key: String) {
        val decayed = _scores.value.mapValues { it.value * DECAY }.filterValues { it > 0.05f }
        val updated = decayed + (key to (decayed[key] ?: 0f) + 1f)
        _scores.value = updated
        prefs.edit().clear().apply {
            updated.forEach { (k, v) -> putFloat(k, v) }
        }.apply()
    }

    private companion object {
        const val DECAY = 0.98f
    }
}
