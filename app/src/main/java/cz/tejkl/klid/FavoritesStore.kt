package cz.tejkl.klid

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Seřazený seznam klíčů aplikací na ploše, uložený v SharedPreferences. */
class FavoritesStore(context: Context) {
    private val prefs = context.getSharedPreferences("klid", Context.MODE_PRIVATE)
    private val _keys = MutableStateFlow(load())
    val keys: StateFlow<List<String>> = _keys.asStateFlow()

    private fun load(): List<String> =
        prefs.getString(KEY, null)?.split(SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()

    private fun save(list: List<String>) {
        _keys.value = list
        prefs.edit().putString(KEY, list.joinToString(SEPARATOR)).apply()
    }

    fun toggle(key: String) {
        val list = _keys.value
        save(if (key in list) list - key else list + key)
    }

    fun move(key: String, delta: Int) {
        val list = _keys.value.toMutableList()
        val from = list.indexOf(key)
        val to = from + delta
        if (from < 0 || to !in list.indices) return
        list.add(to, list.removeAt(from))
        save(list)
    }

    private companion object {
        const val KEY = "favorites"
        const val SEPARATOR = "\n"
    }
}
