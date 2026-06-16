export interface Env {
  OBSERVATIONS: KVNamespace;
  ALLOWED_ORIGINS?: string;
  SYNC_WRITE_TOKEN?: string;
}

type ThinkletObservationPayload = {
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
  receivedAt?: number;
};

const JSON_HEADERS = {
  'content-type': 'application/json; charset=utf-8',
};

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const corsHeaders = buildCorsHeaders(request, env);

    if (request.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: corsHeaders });
    }

    try {
      if (url.pathname === '/health') {
        return json({ ok: true }, corsHeaders);
      }

      if (url.pathname === '/observations' && request.method === 'POST') {
        return await createObservation(request, env, corsHeaders);
      }

      if (url.pathname === '/observations' && request.method === 'GET') {
        return await listObservations(url, env, corsHeaders);
      }

      return json({ error: 'not_found' }, corsHeaders, 404);
    } catch (error) {
      console.error(JSON.stringify({ message: 'request_failed', error: String(error) }));
      return json({ error: 'internal_error' }, corsHeaders, 500);
    }
  },
};

async function createObservation(
  request: Request,
  env: Env,
  corsHeaders: HeadersInit,
): Promise<Response> {
  if (!(await isAuthorized(request, env))) {
    return json({ error: 'unauthorized' }, corsHeaders, 401);
  }

  const payload = await request.json<ThinkletObservationPayload>();
  const now = Date.now();
  const id = sanitizeId(payload.id) ?? `thinklet-${now}-${crypto.randomUUID()}`;
  const normalized: ThinkletObservationPayload = {
    ...payload,
    id,
    source: 'THINKLET',
    receivedAt: now,
  };

  await env.OBSERVATIONS.put(`obs:${id}`, JSON.stringify(normalized), {
    metadata: {
      receivedAt: now,
      observedAt: normalizeObservedAt(payload.observedAt),
    },
  });

  return json({ ok: true, id }, corsHeaders, 201);
}

async function listObservations(
  url: URL,
  env: Env,
  corsHeaders: HeadersInit,
): Promise<Response> {
  const since = Number(url.searchParams.get('since') ?? '0');
  const listed = await env.OBSERVATIONS.list({ prefix: 'obs:', limit: 1000 });
  const observations: ThinkletObservationPayload[] = [];

  for (const key of listed.keys) {
    const metadata = key.metadata as { receivedAt?: number } | undefined;
    if (metadata?.receivedAt && metadata.receivedAt <= since) {
      continue;
    }
    const raw = await env.OBSERVATIONS.get(key.name);
    if (!raw) {
      continue;
    }
    observations.push(JSON.parse(raw) as ThinkletObservationPayload);
  }

  observations.sort((a, b) => Number(a.receivedAt ?? 0) - Number(b.receivedAt ?? 0));
  return json({ observations, serverTime: Date.now() }, corsHeaders);
}

async function isAuthorized(request: Request, env: Env): Promise<boolean> {
  if (!env.SYNC_WRITE_TOKEN) {
    return true;
  }
  const header = request.headers.get('authorization') ?? '';
  const token = header.startsWith('Bearer ') ? header.slice('Bearer '.length) : '';
  return await timingSafeEqual(token, env.SYNC_WRITE_TOKEN);
}

async function timingSafeEqual(a: string, b: string): Promise<boolean> {
  const encoder = new TextEncoder();
  const left = encoder.encode(a);
  const right = encoder.encode(b);
  if (left.length !== right.length) {
    return false;
  }
  const key = await crypto.subtle.importKey(
    'raw',
    encoder.encode('kamiyama-sync-compare-key'),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign'],
  );
  const leftMac = await crypto.subtle.sign('HMAC', key, left);
  const rightMac = await crypto.subtle.sign('HMAC', key, right);
  return equalBytes(new Uint8Array(leftMac), new Uint8Array(rightMac));
}

function equalBytes(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) {
    return false;
  }
  let diff = 0;
  for (let index = 0; index < a.length; index += 1) {
    diff |= a[index] ^ b[index];
  }
  return diff === 0;
}

function buildCorsHeaders(request: Request, env: Env): HeadersInit {
  const origin = request.headers.get('origin') ?? '';
  const allowedOrigins = (env.ALLOWED_ORIGINS ?? '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
  const allowedOrigin = allowedOrigins.includes(origin) ? origin : allowedOrigins[0] ?? '*';
  return {
    'access-control-allow-origin': allowedOrigin,
    'access-control-allow-methods': 'GET,POST,OPTIONS',
    'access-control-allow-headers': 'content-type,authorization',
    'access-control-max-age': '86400',
  };
}

function json(data: unknown, corsHeaders: HeadersInit, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      ...JSON_HEADERS,
      ...corsHeaders,
    },
  });
}

function sanitizeId(id: unknown): string | null {
  if (typeof id !== 'string') {
    return null;
  }
  const normalized = id.trim().replace(/[^a-zA-Z0-9_-]/g, '-').slice(0, 96);
  return normalized || null;
}

function normalizeObservedAt(value: unknown): number {
  if (typeof value === 'number') {
    return value;
  }
  if (typeof value === 'string') {
    const parsed = Date.parse(value);
    if (!Number.isNaN(parsed)) {
      return parsed;
    }
  }
  return Date.now();
}
