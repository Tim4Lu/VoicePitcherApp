package com.example.voicepitcher

import android.content.Context
import org.json.JSONObject

data class PresetData(
    val pitch: Float,
    val volume: Float,
    val bandsProgress: Map<Int, Int>,
    val bandsEnabled: Map<Int, Boolean>
)

class PresetManager(context: Context) {
    private val prefs = context.getSharedPreferences("VoicePitcherPresets", Context.MODE_PRIVATE)

    fun savePreset(name: String, pitch: Float, volume: Float, progressMap: Map<Int, Int>, enabledMap: Map<Int, Boolean>) {
        val json = JSONObject()
        json.put("pitch", pitch)
        json.put("volume", volume)

        val bandsJson = JSONObject()
        for ((k, v) in progressMap) {
            bandsJson.put(k.toString(), v)
        }
        json.put("bands", bandsJson)

        val enabledJson = JSONObject()
        for ((k, v) in enabledMap) {
            enabledJson.put(k.toString(), v)
        }
        json.put("enabled", enabledJson)

        prefs.edit().putString(name, json.toString()).apply()
    }

    fun loadPreset(name: String): PresetData? {
        val jsonStr = prefs.getString(name, null) ?: return null
        return try {
            val json = JSONObject(jsonStr)
            val pitch = json.getDouble("pitch").toFloat()
            val volume = json.getDouble("volume").toFloat()

            val bandsMap = mutableMapOf<Int, Int>()
            val bandsJson = json.getJSONObject("bands")
            val keys = bandsJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                bandsMap[key.toInt()] = bandsJson.getInt(key)
            }

            val enabledMap = mutableMapOf<Int, Boolean>()
            val enabledJson = json.getJSONObject("enabled")
            val enabledKeys = enabledJson.keys()
            while (enabledKeys.hasNext()) {
                val key = enabledKeys.next()
                enabledMap[key.toInt()] = enabledJson.getBoolean(key)
            }

            PresetData(pitch, volume, bandsMap, enabledMap)
        } catch (e: Exception) {
            null
        }
    }

    fun getPresetNames(): List<String> {
        val keys = prefs.all.keys.toList()
        return if (keys.isEmpty()) listOf("Немає пресетів") else keys
    }
}
