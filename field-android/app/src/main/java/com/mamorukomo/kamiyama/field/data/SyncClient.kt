package com.mamorukomo.kamiyama.field.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SyncClient(private val endpoint: String) {
    val isConfigured: Boolean = endpoint.isNotBlank()

    suspend fun pullObservations(): List<Observation> {
        if (!isConfigured) {
            return emptyList()
        }
        return withContext(Dispatchers.IO) {
            val connection = (URL("${endpoint.trimEnd('/')}/observations").openConnection() as HttpURLConnection)
                .apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 15000
                }
            try {
                val status = connection.responseCode
                if (status !in 200..299) {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw IllegalStateException("HTTP $status $error")
                }
                val raw = connection.inputStream.bufferedReader().use { it.readText() }
                parseObservations(raw)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun parseObservations(raw: String): List<Observation> {
        val root = JSONObject(raw)
        val observations = root.optJSONArray("observations") ?: return emptyList()
        return buildList {
            for (index in 0 until observations.length()) {
                val item = observations.optJSONObject(index) ?: continue
                add(item.toObservation())
            }
        }
    }

    private fun JSONObject.toObservation(): Observation {
        val analysis = optJSONObject("aiAnalysis")
        val point = LatLng(
            latitude = optDoubleOrNull("latitude") ?: KamiyamaCenter.latitude,
            longitude = optDoubleOrNull("longitude") ?: KamiyamaCenter.longitude,
        )
        val observedAt = normalizeObservedAt(opt("observedAt"))
        val category = when (analysis?.optString("category") ?: optString("category")) {
            "insect" -> SpeciesCategory.Insect
            else -> SpeciesCategory.Plant
        }
        val label = analysis?.optString("commonName")?.takeIf { it.isNotBlank() }
            ?: optString("aiLabel").takeIf { it.isNotBlank() }
            ?: optString("label").takeIf { it.isNotBlank() }
            ?: "Thinklet観察"
        val candidate = matchCandidate(category, analysis, label)
            ?: suggestCandidates(category, point, observedAt).firstOrNull()?.candidate
        val note = buildNote(this, analysis)

        return Observation(
            id = optString("id").takeIf { it.isNotBlank() } ?: "thinklet-$observedAt",
            photoUri = optString("photoDataUrl").takeIf { it.startsWith("data:image/") }
                ?: optString("photoUri").takeIf { it.isNotBlank() }
                ?: buildPhotoPlaceholder(label, category),
            category = category,
            candidateId = candidate?.id,
            customName = label,
            note = note,
            latitude = point.latitude,
            longitude = point.longitude,
            accuracy = optDoubleOrNull("accuracyMeters")?.toFloat(),
            observedAtMillis = observedAt,
            environment = describeEnvironment(point),
            rarity = inferRarity(candidate, point, observedAt),
        )
    }

    private fun matchCandidate(
        category: SpeciesCategory,
        analysis: JSONObject?,
        label: String,
    ): SpeciesCandidate? {
        val scientificName = analysis?.optString("scientificName")?.takeIf { it.isNotBlank() }
        return SpeciesCandidates.firstOrNull { candidate ->
            candidate.category == category &&
                (candidate.commonName == label || scientificName == candidate.scientificName)
        }
    }

    private fun buildNote(item: JSONObject, analysis: JSONObject?): String {
        val lines = mutableListOf("THINKLETから同期API経由で取り込んだ観察です。")
        val label = item.optString("label").takeIf { it.isNotBlank() }
        val confidence = item.optDoubleOrNull("confidence")
        if (label != null) {
            lines += if (confidence != null) {
                "端末ラベル: $label (${(confidence * 100).toInt()}%)"
            } else {
                "端末ラベル: $label"
            }
        }
        val aiName = analysis?.optString("commonName")?.takeIf { it.isNotBlank() }
        val aiConfidence = analysis?.optDoubleOrNull("confidence")
        if (aiName != null) {
            lines += if (aiConfidence != null) {
                "AI判定: $aiName (${(aiConfidence * 100).toInt()}%)"
            } else {
                "AI判定: $aiName"
            }
        }
        analysis?.optString("scientificName")
            ?.takeIf { it.isNotBlank() }
            ?.let { lines += "学名候補: $it" }
        analysis?.optString("reason")
            ?.takeIf { it.isNotBlank() }
            ?.let { lines += "判定根拠: $it" }
        item.optString("photoUri")
            .takeIf { it.isNotBlank() }
            ?.let { lines += "Thinklet保存先: $it" }
        return lines.joinToString("\n")
    }

    private fun JSONObject.optDoubleOrNull(name: String): Double? {
        return if (has(name) && !isNull(name)) optDouble(name) else null
    }

    private fun normalizeObservedAt(value: Any?): Long {
        return when (value) {
            is Number -> value.toLong()
            is String -> runCatching { java.time.Instant.parse(value).toEpochMilli() }
                .getOrDefault(System.currentTimeMillis())
            else -> System.currentTimeMillis()
        }
    }

    private fun buildPhotoPlaceholder(label: String, category: SpeciesCategory): String {
        val color = if (category == SpeciesCategory.Insect) "#8f5e2f" else "#668f3b"
        val escaped = label.take(18)
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="720" height="720" viewBox="0 0 720 720"><rect width="720" height="720" fill="#edf3ec"/><circle cx="360" cy="306" r="148" fill="$color"/><text x="360" y="318" text-anchor="middle" font-family="sans-serif" font-size="68" font-weight="700" fill="#fff">THINKLET</text><text x="360" y="515" text-anchor="middle" font-family="sans-serif" font-size="42" font-weight="700" fill="#14231a">$escaped</text></svg>"""
        return "data:image/svg+xml;charset=UTF-8,${java.net.URLEncoder.encode(svg, "UTF-8")}"
    }
}
