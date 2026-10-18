package com.hfmelt.virtualgps

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Favorite(val name: String, val coordinates: Coordinates)
class LocationStore(context: Context) {
    private val prefs = context.getSharedPreferences("locations", Context.MODE_PRIVATE)
    fun selected(): Coordinates = runCatching {
        Coordinates(prefs.getString("lat", "33.3152")!!.toDouble(), prefs.getString("lon", "44.3661")!!.toDouble())
    }.getOrDefault(Coordinates(33.3152, 44.3661))
    fun select(point: Coordinates) { prefs.edit().putString("lat", point.latitude.toString()).putString("lon", point.longitude.toString()).apply() }
    fun favorites(): List<Favorite> = runCatching {
        val array = JSONArray(prefs.getString("favorites", "[]"))
        (0 until array.length()).map {
            val item = array.getJSONObject(it)
            Favorite(item.getString("name"), Coordinates(item.getDouble("lat"), item.getDouble("lon")))
        }
    }.getOrDefault(emptyList())
    fun save(items: List<Favorite>) {
        val array = JSONArray()
        items.forEach { array.put(JSONObject().put("name", it.name).put("lat", it.coordinates.latitude).put("lon", it.coordinates.longitude)) }
        prefs.edit().putString("favorites", array.toString()).apply()
    }
}
