package com.vibecode.mobile.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ConnectionStore(context: Context) {
    private val prefs = context.getSharedPreferences("vibecode", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    fun loadMachines(): List<MachineConfig> {
        val raw = prefs.getString("machines", null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<MachineConfig>>(raw) }.getOrDefault(emptyList())
    }
    fun saveMachines(items: List<MachineConfig>) { prefs.edit().putString("machines", json.encodeToString(items)).apply() }
    fun selectedId(): String? = prefs.getString("selectedMachine", null)
    fun select(id:String) { prefs.edit().putString("selectedMachine", id).apply() }
}
