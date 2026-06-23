export interface Env {
  OBSERVATIONS: KVNamespace;
  ALLOWED_ORIGINS?: string;
  SYNC_WRITE_TOKEN?: string;
  OPENAI_API_KEY?: string;
  OPENAI_MODEL?: string;
}

type ThinkletObservationPayload = {
  id?: string;
  source?: string;
  category?: string;
  label?: string;
  confidence?: number | null;
  aiLabel?: string | null;
  aiConfidence?: number | null;
  aiAnalysis?: SpeciesAnalysis | null;
  latitude?: number | null;
  longitude?: number | null;
  accuracyMeters?: number | null;
  observedAt?: number | string;
  photoUri?: string | null;
  photoBase64?: string | null;
  photoMimeType?: string | null;
  photoDataUrl?: string | null;
  receivedAt?: number;
};

type SpeciesAnalysis = {
  category: 'plant' | 'insect' | 'unknown';
  commonName: string;
  scientificName?: string | null;
  confidence: number;
  reason: string;
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
  const photoDataUrl = buildPhotoDataUrl(payload);
  const aiAnalysis = await analyzeSpeciesPhoto(payload, photoDataUrl, env);
  const normalized: ThinkletObservationPayload = {
    ...payload,
    id,
    source: 'THINKLET',
    category: normalizeCategory(aiAnalysis?.category ?? payload.category),
    label: aiAnalysis?.commonName ?? payload.label ?? 'Thinklet観察',
    confidence: aiAnalysis?.confidence ?? payload.confidence ?? null,
    aiLabel: aiAnalysis?.commonName ?? null,
    aiConfidence: aiAnalysis?.confidence ?? null,
    aiAnalysis,
    photoBase64: undefined,
    photoMimeType: undefined,
    photoDataUrl,
    receivedAt: now,
  };

  await env.OBSERVATIONS.put(`obs:${id}`, JSON.stringify(normalized), {
    metadata: {
      receivedAt: now,
      observedAt: normalizeObservedAt(payload.observedAt),
    },
  });

  return json({ ok: true, id, observation: normalized }, corsHeaders, 201);
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

function normalizeCategory(value: unknown): 'plant' | 'insect' {
  return value === 'insect' ? 'insect' : 'plant';
}

function buildPhotoDataUrl(payload: ThinkletObservationPayload): string | null {
  if (payload.photoDataUrl?.startsWith('data:image/')) {
    return payload.photoDataUrl;
  }
  if (!payload.photoBase64) {
    return null;
  }
  const mimeType = payload.photoMimeType?.startsWith('image/')
    ? payload.photoMimeType
    : 'image/jpeg';
  return `data:${mimeType};base64,${payload.photoBase64}`;
}

async function analyzeSpeciesPhoto(
  payload: ThinkletObservationPayload,
  photoDataUrl: string | null,
  env: Env,
): Promise<SpeciesAnalysis | null> {
  if (!photoDataUrl || !env.OPENAI_API_KEY) {
    return null;
  }

  const response = await fetch('https://api.openai.com/v1/responses', {
    method: 'POST',
    headers: {
      authorization: `Bearer ${env.OPENAI_API_KEY}`,
      'content-type': 'application/json',
    },
    body: JSON.stringify({
      model: env.OPENAI_MODEL || 'gpt-4o-mini',
      input: [
        {
          role: 'user',
          content: [
            {
              type: 'input_text',
              text: [
                '神山町の自然観察アプリに登録するため、この写真の生物を判定してください。',
                '対象は植物または虫です。断定しすぎず、画像から分かる範囲で答えてください。',
                'JSONだけを返してください。',
                '{"category":"plant|insect|unknown","commonName":"日本語名または未同定の植物/虫","scientificName":"学名またはnull","confidence":0.0,"reason":"短い根拠"}',
                `端末側の簡易ラベル: ${payload.label ?? 'なし'}`,
              ].join('\n'),
            },
            {
              type: 'input_image',
              image_url: photoDataUrl,
            },
          ],
        },
      ],
      max_output_tokens: 500,
    }),
  });

  if (!response.ok) {
    console.error(JSON.stringify({
      message: 'openai_analysis_failed',
      status: response.status,
      body: await response.text(),
    }));
    return null;
  }

  const data = await response.json<Record<string, unknown>>();
  const outputText = extractOutputText(data);
  const parsed = parseJsonObject(outputText);
  if (!parsed) {
    console.error(JSON.stringify({ message: 'openai_analysis_parse_failed', outputText }));
    return null;
  }

  const category = parsed.category === 'insect' || parsed.category === 'plant'
    ? parsed.category
    : 'unknown';
  const commonName = typeof parsed.commonName === 'string' && parsed.commonName.trim()
    ? parsed.commonName.trim()
    : payload.label ?? '未同定';
  const scientificName = typeof parsed.scientificName === 'string' && parsed.scientificName.trim()
    ? parsed.scientificName.trim()
    : null;
  const confidence = typeof parsed.confidence === 'number'
    ? Math.max(0, Math.min(1, parsed.confidence))
    : 0.5;
  const reason = typeof parsed.reason === 'string' && parsed.reason.trim()
    ? parsed.reason.trim()
    : '画像AIによる推定です。';

  return { category, commonName, scientificName, confidence, reason };
}

function extractOutputText(data: Record<string, unknown>): string {
  if (typeof data.output_text === 'string') {
    return data.output_text;
  }
  const output = Array.isArray(data.output) ? data.output : [];
  return output
    .flatMap((item) => {
      if (!item || typeof item !== 'object') {
        return [];
      }
      const content = (item as { content?: unknown }).content;
      return Array.isArray(content) ? content : [];
    })
    .map((content) => {
      if (!content || typeof content !== 'object') {
        return '';
      }
      const text = (content as { text?: unknown }).text;
      return typeof text === 'string' ? text : '';
    })
    .join('\n')
    .trim();
}

function parseJsonObject(value: string): Record<string, unknown> | null {
  const cleaned = value
    .trim()
    .replace(/^```(?:json)?/i, '')
    .replace(/```$/i, '')
    .trim();
  const start = cleaned.indexOf('{');
  const end = cleaned.lastIndexOf('}');
  if (start === -1 || end === -1 || end <= start) {
    return null;
  }
  try {
    const parsed = JSON.parse(cleaned.slice(start, end + 1));
    return parsed && typeof parsed === 'object' ? parsed as Record<string, unknown> : null;
  } catch {
    return null;
  }
}
