package com.kal.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kal.core.Meal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.kalDataStore: DataStore<Preferences> by preferencesDataStore("kal")

data class Snapshot(
    val maintenance: Int = 2200,
    val deficit: Int = 500,
    val meals: List<Meal> = emptyList(),
    val onboarded: Boolean = false,
    val hintUses: Int = 0,
    val loaded: Boolean = false,
)

class KalStore(private val ctx: Context) {
    private val kMaint = intPreferencesKey("maintenance")
    private val kDef = intPreferencesKey("deficit")
    private val kMeals = stringPreferencesKey("meals")
    private val kOnb = booleanPreferencesKey("onboarded")
    private val kHint = intPreferencesKey("hint_uses")

    val flow: Flow<Snapshot> = ctx.kalDataStore.data.map { p ->
        Snapshot(
            maintenance = p[kMaint] ?: 2200,
            deficit = p[kDef] ?: 500,
            meals = decode(p[kMeals].orEmpty()),
            onboarded = p[kOnb] ?: false,
            hintUses = p[kHint] ?: 0,
            loaded = true,
        )
    }

    suspend fun setMaintenance(v: Int) = ctx.kalDataStore.edit { it[kMaint] = v }
    suspend fun setDeficit(v: Int) = ctx.kalDataStore.edit { it[kDef] = v }
    suspend fun markOnboarded() = ctx.kalDataStore.edit { it[kOnb] = true }
    suspend fun bumpHint() = ctx.kalDataStore.edit { it[kHint] = (it[kHint] ?: 0) + 1 }

    suspend fun addMeal(m: Meal) = ctx.kalDataStore.edit {
        val cutoff = m.epochMs - 4L * 24 * 3600 * 1000      // keep a few days only
        val list = decode(it[kMeals].orEmpty()).filter { x -> x.epochMs >= cutoff } + m
        it[kMeals] = encode(list)
    }

    suspend fun removeMeal(id: Long) = ctx.kalDataStore.edit {
        it[kMeals] = encode(decode(it[kMeals].orEmpty()).filter { x -> x.id != id })
    }

    private fun encode(l: List<Meal>) =
        l.joinToString("\n") { "${it.id}|${it.epochMs}|${it.kcal}|${it.name.replace('|', ' ').replace('\n', ' ')}" }

    private fun decode(s: String): List<Meal> = s.lineSequence().mapNotNull { line ->
        val p = line.split('|', limit = 4)
        if (p.size < 4) null else runCatching { Meal(p[0].toLong(), p[1].toLong(), p[2].toInt(), p[3]) }.getOrNull()
    }.toList()
}
