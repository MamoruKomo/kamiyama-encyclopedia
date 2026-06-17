package com.mamorukomo.kamiyama.field.data

import java.time.Instant
import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

fun distanceMeters(a: LatLng, b: LatLng): Double {
    val earthRadius = 6_371_000.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLng = Math.toRadians(b.longitude - a.longitude)
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val sinLat = sin(dLat / 2)
    val sinLng = sin(dLng / 2)
    val h = sinLat.pow(2) + cos(lat1) * cos(lat2) * sinLng.pow(2)
    return 2 * earthRadius * asin(sqrt(h))
}

fun describeEnvironment(point: LatLng): String {
    return nearestZone(point)?.name ?: "神山フィールド"
}

fun suggestCandidates(
    category: SpeciesCategory,
    point: LatLng,
    observedAtMillis: Long = System.currentTimeMillis(),
): List<Suggestion> {
    val month = Instant.ofEpochMilli(observedAtMillis)
        .atZone(ZoneId.systemDefault())
        .monthValue

    return SpeciesCandidates
        .asSequence()
        .filter { it.category == category }
        .map { candidate ->
            val distance = nearestKnownDistanceMeters(candidate, point)
            val seasonalBoost = if (candidate.seasonMonths.contains(month)) 0.0 else 1600.0
            val rarityBoost = candidate.rarity.score * 180.0
            Triple(candidate, distance, distance + seasonalBoost - rarityBoost)
        }
        .sortedBy { it.third }
        .take(4)
        .map { Suggestion(candidate = it.first, distanceMeters = it.second) }
        .toList()
}

fun inferRarity(
    candidate: SpeciesCandidate?,
    point: LatLng,
    observedAtMillis: Long = System.currentTimeMillis(),
): Rarity {
    if (candidate == null) {
        return Rarity.Special
    }

    val month = Instant.ofEpochMilli(observedAtMillis)
        .atZone(ZoneId.systemDefault())
        .monthValue
    val distance = nearestKnownDistanceMeters(candidate, point)
    var score = candidate.rarity.score
    if (!candidate.seasonMonths.contains(month)) {
        score += 1
    }
    if (distance > 2500) {
        score += 1
    }
    return when (min(score, 4)) {
        1 -> Rarity.Common
        2 -> Rarity.Uncommon
        3 -> Rarity.Rare
        else -> Rarity.Special
    }
}

private fun nearestZone(point: LatLng): NatureZone? {
    return NatureZones.minByOrNull { zone -> distanceMeters(point, polygonCenter(zone.polygon)) }
}

private fun nearestKnownDistanceMeters(candidate: SpeciesCandidate, point: LatLng): Double {
    return candidate.knownLocations.minOf { distanceMeters(it, point) }
}

private fun polygonCenter(points: List<LatLng>): LatLng {
    return LatLng(
        latitude = points.sumOf { it.latitude } / points.size,
        longitude = points.sumOf { it.longitude } / points.size,
    )
}
