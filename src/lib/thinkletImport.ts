import { KAMIYAMA_CENTER, speciesCandidates } from '../data/kamiyama';
import { describeEnvironment, inferRarity, suggestCandidates } from './geo';
import type { LatLng, Observation, SpeciesCategory } from '../types/domain';

type ThinkletPayload = {
  id?: string;
  source?: string;
  category?: string;
  label?: string;
  confidence?: number | null;
  latitude?: number | null;
  longitude?: number | null;
  accuracyMeters?: number | null;
  observedAt?: number | string;
  photoUri?: string | null;
};

export function readThinkletObservationFromUrl(): Observation | null {
  if (typeof window === 'undefined') {
    return null;
  }

  const url = new URL(window.location.href);
  const raw = url.searchParams.get('thinkletObservation');
  if (!raw) {
    return null;
  }

  try {
    const payload = JSON.parse(raw) as ThinkletPayload;
    const point = normalizePoint(payload);
    const observedAt = normalizeObservedAt(payload.observedAt);
    const category = normalizeCategory(payload.category);
    const suggested = suggestCandidates(speciesCandidates, category, point, new Date(observedAt))[0]
      ?.candidate ?? null;
    const label = payload.label?.trim() || suggested?.commonName || 'Thinklet観察';

    return {
      id: payload.id || `thinklet-${Date.parse(observedAt)}`,
      photoUri: buildThinkletPlaceholder(label, category),
      category,
      candidateId: suggested?.id ?? null,
      customName: label,
      note: buildNote(payload),
      latitude: point.latitude,
      longitude: point.longitude,
      accuracy: typeof payload.accuracyMeters === 'number' ? payload.accuracyMeters : null,
      observedAt,
      environment: describeEnvironment(point),
      rarity: inferRarity(suggested, point, new Date(observedAt)),
      source: 'thinklet',
      externalPhotoUri: payload.photoUri ?? undefined,
      aiLabel: payload.label,
      aiConfidence: typeof payload.confidence === 'number' ? payload.confidence : null,
    };
  } catch {
    return null;
  }
}

export function clearThinkletObservationParam() {
  if (typeof window === 'undefined') {
    return;
  }
  const url = new URL(window.location.href);
  if (!url.searchParams.has('thinkletObservation')) {
    return;
  }
  url.searchParams.delete('thinkletObservation');
  window.history.replaceState({}, '', url.toString());
}

function normalizePoint(payload: ThinkletPayload): LatLng {
  if (typeof payload.latitude === 'number' && typeof payload.longitude === 'number') {
    return {
      latitude: payload.latitude,
      longitude: payload.longitude,
    };
  }
  return KAMIYAMA_CENTER;
}

function normalizeObservedAt(value: ThinkletPayload['observedAt']) {
  if (typeof value === 'number') {
    return new Date(value).toISOString();
  }
  if (typeof value === 'string') {
    const date = new Date(value);
    if (!Number.isNaN(date.getTime())) {
      return date.toISOString();
    }
  }
  return new Date().toISOString();
}

function normalizeCategory(value: ThinkletPayload['category']): SpeciesCategory {
  return value === 'insect' ? 'insect' : 'plant';
}

function buildNote(payload: ThinkletPayload) {
  const lines = ['THINKLETから連携された観察です。'];
  if (payload.label) {
    const confidence = typeof payload.confidence === 'number'
      ? ` (${Math.round(payload.confidence * 100)}%)`
      : '';
    lines.push(`簡易ラベル: ${payload.label}${confidence}`);
  }
  if (payload.photoUri) {
    lines.push(`写真はThinklet側に保存: ${payload.photoUri}`);
  }
  return lines.join('\n');
}

function buildThinkletPlaceholder(label: string, category: SpeciesCategory) {
  const color = category === 'insect' ? '#8f5e2f' : '#668f3b';
  const escaped = escapeXml(label.slice(0, 18));
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="720" height="720" viewBox="0 0 720 720"><rect width="720" height="720" fill="#edf3ec"/><circle cx="360" cy="306" r="148" fill="${color}"/><text x="360" y="318" text-anchor="middle" font-family="sans-serif" font-size="68" font-weight="700" fill="#fff">THINKLET</text><text x="360" y="515" text-anchor="middle" font-family="sans-serif" font-size="42" font-weight="700" fill="#14231a">${escaped}</text></svg>`;
  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
}

function escapeXml(value: string) {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&apos;');
}
