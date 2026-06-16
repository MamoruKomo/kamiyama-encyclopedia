import { natureZones } from '../data/kamiyama';
import type { LatLng, NatureZone, Rarity, SpeciesCandidate, SpeciesCategory } from '../types/domain';

const rarityScore: Record<Rarity, number> = {
  common: 1,
  uncommon: 2,
  rare: 3,
  special: 4,
};

const scoreRarity: Record<number, Rarity> = {
  1: 'common',
  2: 'uncommon',
  3: 'rare',
  4: 'special',
};

export function distanceMeters(a: LatLng, b: LatLng) {
  const earthRadius = 6371000;
  const dLat = toRad(b.latitude - a.latitude);
  const dLng = toRad(b.longitude - a.longitude);
  const lat1 = toRad(a.latitude);
  const lat2 = toRad(b.latitude);
  const sinLat = Math.sin(dLat / 2);
  const sinLng = Math.sin(dLng / 2);
  const h = sinLat * sinLat + Math.cos(lat1) * Math.cos(lat2) * sinLng * sinLng;
  return 2 * earthRadius * Math.asin(Math.sqrt(h));
}

export function nearestKnownDistanceMeters(candidate: SpeciesCandidate, point: LatLng) {
  return Math.min(...candidate.knownLocations.map((location) => distanceMeters(location, point)));
}

export function describeEnvironment(point: LatLng) {
  const zone = nearestZone(point);
  return zone ? zone.name : '神山フィールド';
}

export function nearestZone(point: LatLng): NatureZone | null {
  const ranked = natureZones
    .map((zone) => ({
      zone,
      distance: distanceMeters(point, polygonCenter(zone.polygon)),
    }))
    .sort((a, b) => a.distance - b.distance);
  return ranked[0]?.zone ?? null;
}

export function suggestCandidates(
  candidates: SpeciesCandidate[],
  category: SpeciesCategory,
  point: LatLng,
  date = new Date(),
) {
  const month = date.getMonth() + 1;
  return candidates
    .filter((candidate) => candidate.category === category)
    .map((candidate) => {
      const distance = nearestKnownDistanceMeters(candidate, point);
      const seasonalBoost = candidate.seasonMonths.includes(month) ? 0 : 1600;
      const rarityBoost = rarityScore[candidate.rarity] * 180;
      return {
        candidate,
        distance,
        score: distance + seasonalBoost - rarityBoost,
      };
    })
    .sort((a, b) => a.score - b.score)
    .slice(0, 4);
}

export function inferRarity(candidate: SpeciesCandidate | null, point: LatLng, observedAt = new Date()) {
  if (!candidate) {
    return 'special';
  }

  const month = observedAt.getMonth() + 1;
  const distance = nearestKnownDistanceMeters(candidate, point);
  let score = rarityScore[candidate.rarity];

  if (!candidate.seasonMonths.includes(month)) {
    score += 1;
  }
  if (distance > 2500) {
    score += 1;
  }

  return scoreRarity[Math.min(score, 4)];
}

function polygonCenter(points: LatLng[]): LatLng {
  const total = points.reduce(
    (sum, point) => ({
      latitude: sum.latitude + point.latitude,
      longitude: sum.longitude + point.longitude,
    }),
    { latitude: 0, longitude: 0 },
  );
  return {
    latitude: total.latitude / points.length,
    longitude: total.longitude / points.length,
  };
}

function toRad(value: number) {
  return (value * Math.PI) / 180;
}
