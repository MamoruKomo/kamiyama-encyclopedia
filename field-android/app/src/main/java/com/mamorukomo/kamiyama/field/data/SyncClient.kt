package com.mamorukomo.kamiyama.field.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class SyncResult(
    val observations: List<Observation>,
    val serverTimeMillis: Long,
)

class SyncClient(private val endpoint: String) {
    val isConfigured: Boolean = endpoint.isNotBlank()

    suspend fun pullObservations(sinceMillis: Long = 0L): SyncResult {
        if (!isConfigured) {
            return SyncResult(emptyList(), sinceMillis)
        }
        return withContext(Dispatchers.IO) {
            runCatching { pullV1Observations(sinceMillis) }
                .getOrElse { pullLegacyObservations(sinceMillis) }
        }
    }

    private fun pullV1Observations(sinceMillis: Long): SyncResult {
        val query = if (sinceMillis > 0L) "?since=$sinceMillis" else ""
        val connection = (URL("${endpoint.trimEnd('/')}/api/v1/observations$query").openConnection() as HttpURLConnection)
            .apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 15000
            }
        try {
            val status = connection.responseCode
            if (status == 404 || status == 503) {
                throw IllegalStateException("v1 unavailable")
            }
            if (status !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("HTTP $status $error")
            }
            val raw = connection.inputStream.bufferedReader().use { it.readText() }
            return parseV1Observations(raw)
        } finally {
            connection.disconnect()
        }
    }

    private fun pullLegacyObservations(sinceMillis: Long): SyncResult {
        val query = if (sinceMillis > 0L) "?since=$sinceMillis" else ""
        val connection = (URL("${endpoint.trimEnd('/')}/observations$query").openConnection() as HttpURLConnection)
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
            return parseObservations(raw)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseV1Observations(raw: String): SyncResult {
        val root = JSONObject(raw)
        val serverTime = root.optLong("serverTime", System.currentTimeMillis())
        val observations = root.optJSONArray("observations") ?: return SyncResult(emptyList(), serverTime)
        val confirmed = buildList {
            for (index in 0 until observations.length()) {
                val item = observations.optJSONObject(index) ?: continue
                item.toV1ObservationOrNull()?.let(::add)
            }
        }
        return SyncResult(confirmed, serverTime)
    }

    private fun JSONObject.toV1ObservationOrNull(): Observation? {
        if (optText("status") != "confirmed") {
            return null
        }
        val confirmedSpeciesId = optText("confirmed_species_id") ?: return null
        val candidate = SpeciesCandidates.firstOrNull { it.id == confirmedSpeciesId } ?: return null
        val point = LatLng(
            latitude = optDoubleOrNull("latitude") ?: KamiyamaCenter.latitude,
            longitude = optDoubleOrNull("longitude") ?: KamiyamaCenter.longitude,
        )
        val observedAt = normalizeObservedAt(optText("captured_at"))
        val imageUrl = optText("image_url")?.let { imagePath ->
            if (imagePath.startsWith("/")) "${endpoint.trimEnd('/')}$imagePath" else imagePath
        } ?: buildPhotoPlaceholder(candidate.commonName, candidate.category)
        val reason = optJSONArray("candidates")
            ?.let { candidates ->
                (0 until candidates.length())
                    .mapNotNull { index -> candidates.optJSONObject(index) }
                    .firstOrNull { it.optString("species_id") == confirmedSpeciesId }
                    ?.optText("reason")
            }
        val note = buildList {
            add("THINKLETから届いた写真を候補カードで確認済みです。")
            reason?.let { add("候補理由: $it") }
            optText("image_sha256")?.let { add("写真SHA-256: ${it.take(12)}...") }
        }.joinToString("\n")
        return Observation(
            id = optText("id") ?: return null,
            photoUri = imageUrl,
            category = candidate.category,
            candidateId = candidate.id,
            customName = candidate.commonName,
            note = note,
            latitude = point.latitude,
            longitude = point.longitude,
            accuracy = optDoubleOrNull("location_accuracy_m")?.toFloat(),
            observedAtMillis = observedAt,
            environment = describeEnvironment(point),
            rarity = candidate.rarity,
        )
    }

    private fun parseObservations(raw: String): SyncResult {
        val root = JSONObject(raw)
        val serverTime = root.optLong("serverTime", System.currentTimeMillis())
        val observations = root.optJSONArray("observations") ?: return SyncResult(emptyList(), serverTime)
        val knownObservations = buildList {
            for (index in 0 until observations.length()) {
                val item = observations.optJSONObject(index) ?: continue
                item.toObservationOrNull()?.let(::add)
            }
        }
        return SyncResult(knownObservations, serverTime)
    }

    private fun JSONObject.toObservationOrNull(): Observation? {
        val analysis = optJSONObject("aiAnalysis") ?: return null
        val isThinklet = optText("source") == "THINKLET" || optText("id")?.startsWith("thinklet-") == true
        val point = LatLng(
            latitude = optDoubleOrNull("latitude") ?: KamiyamaCenter.latitude,
            longitude = optDoubleOrNull("longitude") ?: KamiyamaCenter.longitude,
        )
        val observedAt = normalizeObservedAt(opt("observedAt"))
        val category = when (analysis.optText("category")) {
            "insect" -> SpeciesCategory.Insect
            "plant" -> SpeciesCategory.Plant
            else -> return null
        }
        val label = analysis.optText("commonName")
            ?.takeUnless { it.isUndeterminedName() }
            ?: return null
        val matchedCandidate = matchCandidate(category, analysis, label)
        val candidate = matchedCandidate
            ?: if (isThinklet) null else suggestCandidates(category, point, observedAt).firstOrNull()?.candidate
        val note = buildNote(this, analysis)
        val aiRarity = analysis.optRarity()
        val rarity = when {
            aiRarity != null -> aiRarity
            category == SpeciesCategory.Insect && candidate != null -> candidate.rarity
            isThinklet && candidate == null -> Rarity.Common
            else -> inferRarity(candidate, point, observedAt)
        }

        return Observation(
            id = optText("id") ?: "thinklet-$observedAt",
            photoUri = optText("photoDataUrl")?.takeIf { it.startsWith("data:image/") }
                ?: optText("photoUri")
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
            rarity = rarity,
        )
    }

    private fun matchCandidate(
        category: SpeciesCategory,
        analysis: JSONObject?,
        label: String,
    ): SpeciesCandidate? {
        val scientificName = analysis?.optText("scientificName")
        return SpeciesCandidates.firstOrNull { candidate ->
            candidate.category == category &&
                (candidate.commonName == label || scientificName == candidate.scientificName)
        }
    }

    private fun buildNote(item: JSONObject, analysis: JSONObject?): String {
        val lines = mutableListOf("THINKLETから同期API経由で取り込んだ観察です。")
        val label = item.optText("label")
        val confidence = item.optDoubleOrNull("confidence")
        if (label != null) {
            lines += if (confidence != null) {
                "端末ラベル: $label (${(confidence * 100).toInt()}%)"
            } else {
                "端末ラベル: $label"
            }
        }
        val aiName = analysis?.optText("commonName")
        val aiConfidence = analysis?.optDoubleOrNull("confidence")
        if (aiName != null) {
            lines += if (aiConfidence != null) {
                "AIのよそう: $aiName (${(aiConfidence * 100).toInt()}%)"
            } else {
                "AIのよそう: $aiName"
            }
        }
        analysis?.optText("scientificName")
            ?.let { lines += "学名候補: $it" }
        analysis?.optText("reason")
            ?.let { lines += "判定根拠: $it" }
        analysis?.optRarity()
            ?.let { lines += "AIレア度: ${it.label}" }
        item.optText("photoUri")
            ?.let { uri -> lines += "Thinklet写真: ${uri.substringAfterLast('/')}" }
        return lines.joinToString("\n")
    }

    private fun JSONObject.optText(name: String): String? {
        if (!has(name) || isNull(name)) {
            return null
        }
        return optString(name)
            .trim()
            .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    }

    private fun String.isUndeterminedName(): Boolean {
        val trimmed = trim()
        return trimmed == "未同定" ||
            trimmed.startsWith("未同定の") ||
            trimmed.equals("unknown", ignoreCase = true) ||
            trimmed.equals("undetermined", ignoreCase = true)
    }

    private fun JSONObject.optDoubleOrNull(name: String): Double? {
        return if (has(name) && !isNull(name)) optDouble(name) else null
    }

    private fun JSONObject.optRarity(): Rarity? {
        return when (optText("rarity")?.lowercase()) {
            "common" -> Rarity.Common
            "uncommon" -> Rarity.Uncommon
            "rare" -> Rarity.Rare
            "special" -> Rarity.Special
            else -> null
        }
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
