import { observationFromThinkletPayload, type ThinkletPayload } from './thinkletImport';
import type { Observation } from '../types/domain';

const ENDPOINT_STORAGE_KEY = 'kamiyamaSyncEndpoint';
const CURSOR_STORAGE_KEY = 'kamiyamaSyncCursor';

type SyncResponse = {
  observations?: ThinkletPayload[];
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
  return window.localStorage.getItem(ENDPOINT_STORAGE_KEY);
}

export async function pullThinkletObservations(): Promise<Observation[]> {
  const endpoint = getSyncEndpoint();
  if (!endpoint) {
    return [];
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
