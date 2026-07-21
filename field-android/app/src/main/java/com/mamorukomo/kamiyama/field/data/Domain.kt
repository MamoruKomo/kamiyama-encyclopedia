package com.mamorukomo.kamiyama.field.data

enum class SpeciesCategory(val label: String, val chip: String) {
    Plant("植物", "PLANT"),
    Insect("虫", "INSECT"),
    Bird("鳥", "BIRD"),
    Mushroom("きのこ", "MUSHROOM"),
}

enum class Rarity(val label: String, val score: Int) {
    Common("COMMON", 1),
    Uncommon("UNCOMMON", 2),
    Rare("RARE", 3),
    Special("SPECIAL", 4),
}

enum class NatureKind {
    River,
    Forest,
    Village,
    Ridge,
    Field,
}

data class LatLng(
    val latitude: Double,
    val longitude: Double,
)

data class NatureZone(
    val id: String,
    val name: String,
    val kind: NatureKind,
    val description: String,
    val polygon: List<LatLng>,
)

data class SpeciesCandidate(
    val id: String,
    val category: SpeciesCategory,
    val commonName: String,
    val scientificName: String,
    val family: String,
    val hint: String,
    val seasonMonths: Set<Int>,
    val rarity: Rarity,
    val knownLocations: List<LatLng>,
    val sourceUrl: String,
)

data class Observation(
    val id: String,
    val photoUri: String,
    val category: SpeciesCategory,
    val candidateId: String?,
    val customName: String,
    val note: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?,
    val observedAtMillis: Long,
    val environment: String,
    val rarity: Rarity,
    val aiConfidence: Double?,
)

data class Suggestion(
    val candidate: SpeciesCandidate,
    val distanceMeters: Double,
)
