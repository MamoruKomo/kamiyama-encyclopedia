import { KAMIYAMA_CENTER, speciesCandidates } from '../data/kamiyama';
import { describeEnvironment, inferRarity } from './geo';
import { getSyncEndpoint } from './syncApi';
import type { Observation, SpeciesCandidate } from '../types/domain';

export type ReviewCandidate = {
  speciesId: string;
  reason: string;
  candidate: SpeciesCandidate | null;
};

export type ReviewObservation = {
  id: string;
  capturedAt: string;
  imageUri: string;
  latitude: number;
  longitude: number;
  accuracy: number | null;
  broadCategory: string;
  candidates: ReviewCandidate[];
};

type V1Observation = {
  id: string;
  captured_at?: string;
  latitude?: number | null;
  longitude?: number | null;
  location_accuracy_m?: number | null;
  image_url?: string;
  broad_category?: string | null;
  candidates?: Array<{
    species_id?: string;
    reason?: string;
  }>;
  status?: string;
};

type V1Response = {
  observations?: V1Observation[];
  serverTime?: number;
};

export async function pullReviewObservations(): Promise<ReviewObservation[]> {
  const endpoint = getSyncEndpoint();
  if (!endpoint) {
    return [];
  }
  const response = await fetch(`${endpoint}/api/v1/review/observations?status=candidate_ready&limit=100`);
  if (response.status === 404 || response.status === 503) {
    return [];
  }
  if (!response.ok) {
    throw new Error(`候補API HTTP ${response.status}`);
  }
  const data = (await response.json()) as V1Response;
  return (data.observations ?? [])
    .filter((item) => item.status === 'candidate_ready')
    .map((item) => toReviewObservation(endpoint, item))
    .filter((item): item is ReviewObservation => item != null);
}

export async function confirmReviewObservation(
  observation: ReviewObservation,
  speciesId: string,
): Promise<Observation> {
  const endpoint = getSyncEndpoint();
  if (!endpoint) {
    throw new Error('同期APIが未設定です');
  }
  const response = await fetch(`${endpoint}/api/v1/review/observations/${observation.id}/confirm`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ action: 'confirm', species_id: speciesId }),
  });
  if (!response.ok) {
    throw new Error(`候補確定API HTTP ${response.status}`);
  }
  const picked = observation.candidates.find((item) => item.speciesId === speciesId)?.candidate;
  if (!picked) {
    throw new Error('候補が見つかりません');
  }
  return {
    id: observation.id,
    photoUri: observation.imageUri,
    category: picked.category,
    candidateId: picked.id,
    customName: picked.commonName,
    note: 'THINKLETから届いた写真を候補カードで確認しました。',
    latitude: observation.latitude,
    longitude: observation.longitude,
    accuracy: observation.accuracy,
    observedAt: observation.capturedAt,
    environment: describeEnvironment({ latitude: observation.latitude, longitude: observation.longitude }),
    rarity: inferRarity(picked, { latitude: observation.latitude, longitude: observation.longitude }, new Date(observation.capturedAt)),
    source: 'thinklet',
    aiLabel: picked.commonName,
    aiScientificName: picked.scientificName,
    aiReason: observation.candidates.find((item) => item.speciesId === speciesId)?.reason,
  };
}

function toReviewObservation(endpoint: string, item: V1Observation): ReviewObservation | null {
  const point = {
    latitude: typeof item.latitude === 'number' ? item.latitude : KAMIYAMA_CENTER.latitude,
    longitude: typeof item.longitude === 'number' ? item.longitude : KAMIYAMA_CENTER.longitude,
  };
  const candidates = (item.candidates ?? [])
    .map((candidate): ReviewCandidate | null => {
      if (!candidate.species_id) {
        return null;
      }
      const localCandidate = speciesCandidates.find((species) => species.id === candidate.species_id) ?? null;
      return {
        speciesId: candidate.species_id,
        reason: candidate.reason ?? 'AIと季節から候補になりました。',
        candidate: localCandidate,
      };
    })
    .filter((candidate): candidate is ReviewCandidate => candidate != null)
    .slice(0, 3);
  if (candidates.length === 0) {
    return null;
  }
  const imagePath = item.image_url?.startsWith('/')
    ? `${endpoint}${item.image_url}`
    : item.image_url;
  return {
    id: item.id,
    capturedAt: normalizeCapturedAt(item.captured_at),
    imageUri: imagePath || '',
    latitude: point.latitude,
    longitude: point.longitude,
    accuracy: typeof item.location_accuracy_m === 'number' ? item.location_accuracy_m : null,
    broadCategory: item.broad_category ?? 'unknown',
    candidates,
  };
}

function normalizeCapturedAt(value: string | undefined): string {
  if (!value) {
    return new Date().toISOString();
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? new Date().toISOString() : date.toISOString();
}
