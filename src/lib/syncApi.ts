import { KAMIYAMA_CENTER, speciesCandidates } from '../data/kamiyama';
import { describeEnvironment, inferRarity } from './geo';
import { observationFromThinkletPayload, type ThinkletPayload } from './thinkletImport';
import type { Observation } from '../types/domain';

const ENDPOINT_STORAGE_KEY = 'kamiyamaSyncEndpoint';
const CURSOR_STORAGE_KEY = 'kamiyamaSyncCursor';
const DEFAULT_SYNC_ENDPOINT = 'https://kamiyama-encyclopedia-sync.kamiyama-kmc2314.workers.dev';

type SyncResponse = {
  observations?: ThinkletPayload[];
  serverTime?: number;
};

type PublicObservation = {
  id: string;
  captured_at?: string;
  public_latitude?: number | null;
  public_longitude?: number | null;
  image_url?: string;
  broad_category?: string | null;
  confirmed_species_id?: string | null;
  species?: {
    japanese_name?: string;
    scientific_name?: string;
    category?: string;
  } | null;
  quality_score?: number | null;
};

type PublicObservationResponse = {
  observations?: PublicObservation[];
  serverTime?: number;
};

export function configureSyncEndpointFromUrl() {
  if (typeof window === 'undefined') {
    return null;
  }
  const url = new URL(window.location.href);
  const endpoint = url.searchParams.get('syncEndpoint')?.trim();
  if (!endpoint) {
    return null;
  }
  const normalized = endpoint.replace(/\/+$/, '');
  window.localStorage.setItem(ENDPOINT_STORAGE_KEY, normalized);
  url.searchParams.delete('syncEndpoint');
  window.history.replaceState({}, '', url.toString());
  return normalized;
}

export function getSyncEndpoint() {
  if (typeof window === 'undefined') {
    return null;
  }
  return window.localStorage.getItem(ENDPOINT_STORAGE_KEY) ?? DEFAULT_SYNC_ENDPOINT;
}

export async function pullThinkletObservations(): Promise<Observation[]> {
  const endpoint = getSyncEndpoint();
  if (!endpoint) {
    return [];
  }

  const publicObservations = await pullPublicObservations(endpoint);
  if (publicObservations) {
    return publicObservations;
  }

  const since = Number(window.localStorage.getItem(CURSOR_STORAGE_KEY) ?? '0');
  const response = await fetch(`${endpoint}/observations?since=${encodeURIComponent(String(since))}`);
  if (!response.ok) {
    throw new Error(`同期API HTTP ${response.status}`);
  }

  const data = (await response.json()) as SyncResponse;
  if (typeof data.serverTime === 'number') {
    window.localStorage.setItem(CURSOR_STORAGE_KEY, String(data.serverTime));
  }

  return (data.observations ?? []).map(observationFromThinkletPayload);
}

async function pullPublicObservations(endpoint: string): Promise<Observation[] | null> {
  const response = await fetch(`${endpoint}/api/v1/public/observations?limit=500`);
  if (response.status === 404 || response.status === 503) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`公開同期API HTTP ${response.status}`);
  }
  const data = (await response.json()) as PublicObservationResponse;
  if (typeof data.serverTime === 'number') {
    window.localStorage.setItem(CURSOR_STORAGE_KEY, String(data.serverTime));
  }
  return (data.observations ?? [])
    .map((item) => toObservation(endpoint, item))
    .filter((item): item is Observation => item != null);
}

function toObservation(endpoint: string, item: PublicObservation): Observation | null {
  const species = speciesCandidates.find((candidate) => candidate.id === item.confirmed_species_id) ?? null;
  const category = species?.category ?? (item.species?.category === 'insect' ? 'insect' : 'plant');
  const point = {
    latitude: typeof item.public_latitude === 'number' ? item.public_latitude : KAMIYAMA_CENTER.latitude,
    longitude: typeof item.public_longitude === 'number' ? item.public_longitude : KAMIYAMA_CENTER.longitude,
  };
  const observedAt = normalizeCapturedAt(item.captured_at);
  const imageUri = item.image_url?.startsWith('/')
    ? `${endpoint}${item.image_url}`
    : item.image_url;
  const name = species?.commonName
    ?? item.species?.japanese_name
    ?? (category === 'insect' ? '発見した虫' : '発見した植物');
  return {
    id: item.id,
    photoUri: imageUri || '',
    category,
    candidateId: species?.id ?? item.confirmed_species_id ?? null,
    customName: name,
    note: 'THINKLETから届いた写真を確認して図鑑に登録しました。',
    latitude: point.latitude,
    longitude: point.longitude,
    accuracy: null,
    observedAt,
    environment: describeEnvironment(point),
    rarity: inferRarity(species, point, new Date(observedAt)),
    source: 'thinklet',
    aiLabel: name,
    aiScientificName: species?.scientificName ?? item.species?.scientific_name ?? null,
  };
}

function normalizeCapturedAt(value: string | undefined): string {
  if (!value) {
    return new Date().toISOString();
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? new Date().toISOString() : date.toISOString();
}
