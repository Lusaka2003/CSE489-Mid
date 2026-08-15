package com.geolandmarks.app.data.remote

import com.geolandmarks.app.BuildConfig
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

data class LandmarkDto(
    val id: Int,
    val title: String,
    val lat: Double,
    val lon: Double,
    val image: String?,
    val score: Double,
    val visitCount: Int,
    val avgDistance: Double,
    val isActive: Boolean
) {
    fun imageUrl(): String? {
        val path = image?.trim().orEmpty()
        if (path.isEmpty()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        return BuildConfig.API_BASE_URL.trimEnd('/') + "/" + path.trimStart('/')
    }
}

data class JobStatusDto(
    val jobId: Long,
    val status: String,
    val distance: Double?
) {
    val isPending: Boolean get() = status.equals("pending", true) || status.equals("queued", true) || status.equals("processing", true)
    val isDone: Boolean get() = status.equals("done", true) || status.equals("complete", true) || status.equals("completed", true) || status.equals("success", true)
}

fun JsonElement.errorMessage(): String? {
    if (!isJsonObject) return null
    val obj = asJsonObject
    return obj.string("error") ?: obj.string("message") ?: obj.string("detail")
}

fun JsonElement.asLandmarkList(): List<LandmarkDto> {
    val array: JsonArray = when {
        isJsonArray -> asJsonArray
        isJsonObject -> {
            val obj = asJsonObject
            when {
                obj.has("landmarks") && obj.get("landmarks").isJsonArray -> obj.getAsJsonArray("landmarks")
                obj.has("data") && obj.get("data").isJsonArray -> obj.getAsJsonArray("data")
                obj.has("items") && obj.get("items").isJsonArray -> obj.getAsJsonArray("items")
                else -> JsonArray()
            }
        }
        else -> JsonArray()
    }
    return array.mapNotNull { it.toLandmarkOrNull() }
}

fun JsonElement.toJobStatusOrNull(): JobStatusDto? {
    if (!isJsonObject) return null
    val obj = asJsonObject
    val jobId = obj.long("job_id") ?: obj.long("jobId") ?: return null
    val status = obj.string("status") ?: "pending"
    val distance = obj.double("distance")
    return JobStatusDto(jobId, status, distance)
}

private fun JsonElement.toLandmarkOrNull(): LandmarkDto? {
    if (!isJsonObject) return null
    val obj = asJsonObject
    val id = obj.int("id") ?: obj.int("landmark_id") ?: return null
    val title = obj.string("title") ?: obj.string("name") ?: "Untitled"
    val lat = obj.double("lat") ?: obj.double("latitude") ?: 0.0
    val lon = obj.double("lon") ?: obj.double("lng") ?: obj.double("longitude") ?: 0.0
    val image = obj.string("image") ?: obj.string("image_url") ?: obj.string("photo")
    val score = obj.double("score") ?: 0.0
    val visitCount = obj.int("visit_count") ?: obj.int("visits") ?: 0
    val avgDistance = obj.double("avg_distance") ?: 0.0
    val isActive = when {
        obj.has("is_active") -> obj.int("is_active") != 0 && obj.string("is_active") != "0"
        obj.has("deleted") -> obj.int("deleted") == 0 && !obj.bool("deleted")
        else -> true
    }
    return LandmarkDto(id, title, lat, lon, image, score, visitCount, avgDistance, isActive)
}

fun JsonObject.string(key: String): String? {
    val el = get(key) ?: return null
    if (el.isJsonNull) return null
    return if (el.isJsonPrimitive) el.asJsonPrimitive.asString else null
}

fun JsonObject.int(key: String): Int? = double(key)?.toInt()

fun JsonObject.long(key: String): Long? = double(key)?.toLong()

fun JsonObject.double(key: String): Double? {
    val el = get(key) ?: return null
    if (el.isJsonNull || !el.isJsonPrimitive) return null
    val p = el.asJsonPrimitive
    return when {
        p.isNumber -> p.asDouble
        p.isString -> p.asString.toDoubleOrNull()
        else -> null
    }
}

fun JsonObject.bool(key: String): Boolean {
    val el = get(key) ?: return false
    if (el.isJsonNull || !el.isJsonPrimitive) return false
    val p = el.asJsonPrimitive
    return when {
        p.isBoolean -> p.asBoolean
        p.isNumber -> p.asInt != 0
        p.isString -> p.asString == "1" || p.asString.equals("true", true)
        else -> false
    }
}
