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
  rarity?: RarityValue | null;
  confidence: number;
  reason: string;
};

type RarityValue = 'common' | 'uncommon' | 'rare' | 'special';

const DEFAULT_OPENAI_MODEL = 'gpt-5.4-mini';

const PLANT_CANDIDATES = [
  {
    commonName: 'ヤブソテツ',
    scientificName: 'Cyrtomium fortunei',
    rarity: 'common',
    hint: '林縁や石垣にある、つやのあるシダ。葉のまとまりが見えると判断しやすい。',
  },
  {
    commonName: 'ヒメウズ',
    scientificName: 'Semiaquilegia adoxoides',
    rarity: 'uncommon',
    hint: '春に小さな花をつける草。道端や林縁に低く咲く。',
  },
  {
    commonName: 'マンリョウ',
    scientificName: 'Ardisia crenata',
    rarity: 'common',
    hint: '赤い実と光沢のある葉が目印。林の下や半日陰に多い。',
  },
  {
    commonName: 'シュロ',
    scientificName: 'Trachycarpus fortunei',
    rarity: 'common',
    hint: '扇形の大きな葉が特徴。庭木や林縁で見つかりやすい。',
  },
  {
    commonName: 'ジュズダマ',
    scientificName: 'Coix lacryma-jobi',
    rarity: 'rare',
    hint: '水辺や湿った草地に出る。硬い丸い実が連なる。',
  },
] as const satisfies ReadonlyArray<{
  commonName: string;
  scientificName: string;
  rarity: RarityValue;
  hint: string;
}>;

const INSECT_CANDIDATES = [
  {
    commonName: 'キイロスズメバチ',
    scientificName: 'Vespa simillima xanthoptera',
    rarity: 'uncommon',
    hint: '黄色と黒の大型ハチ。安全上、巣や個体に近づきすぎない。',
  },
  {
    commonName: 'ニホンミツバチ',
    scientificName: 'Apis cerana',
    rarity: 'rare',
    hint: '花に来る小型のミツバチ。全体に黒っぽく、腹部の縞が見えることがある。',
  },
  {
    commonName: 'テングチョウ',
    scientificName: 'Libythea lepita',
    rarity: 'uncommon',
    hint: '顔先が突き出て見える蝶。翅は褐色から橙色の模様。',
  },
  {
    commonName: 'ベニシジミ',
    scientificName: 'Lycaena phlaeas daimio',
    rarity: 'common',
    hint: '小型で橙色が目立つ蝶。草地や畑の縁に多い。',
  },
  {
    commonName: 'ツマグロヒョウモン',
    scientificName: 'Argynnis hyperbius',
    rarity: 'common',
    hint: '橙色のヒョウ柄模様を持つ蝶。花の多い場所で見つかりやすい。',
  },
] as const satisfies ReadonlyArray<{
  commonName: string;
  scientificName: string;
  rarity: RarityValue;
  hint: string;
}>;

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

function normalizeCategory(value: unknown): 'plant' | 'insect' | 'unknown' {
  if (value === 'insect' || value === 'plant') {
    return value;
  }
  return 'unknown';
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
      model: env.OPENAI_MODEL || DEFAULT_OPENAI_MODEL,
      input: [
        {
          role: 'user',
          content: [
            {
              type: 'input_text',
              text: [
                '神山町の小学生向け自然観察アプリに登録するため、この写真の生物を推定してください。',
                'これは授業や探検で使うため、断定しすぎず「AIのよそう」として安全で短い表現にしてください。',
                '虫または植物が写っている場合は、下の候補表と照合してください。',
                '候補表に強く一致する生き物は、その日本語名・学名・レア度を返してください。',
                '候補表にない虫は commonName を「未同定の虫」、scientificName を null、rarity を "common" にしてください。',
                '候補表にない植物は commonName を「未同定の植物」、scientificName を null、rarity を "common" にしてください。',
                '植物または虫ではない場合は category を "unknown" にしてください。',
                '危険な虫の可能性がある場合は、reason に「近づかず先生に知らせる」ことを短く含めてください。',
                `植物候補表: ${JSON.stringify(PLANT_CANDIDATES)}`,
                `昆虫候補表: ${JSON.stringify(INSECT_CANDIDATES)}`,
                'JSONだけを返してください。',
                '{"category":"plant|insect|unknown","commonName":"日本語名または未同定の植物/虫","scientificName":"学名またはnull","rarity":"common|uncommon|rare|special|null","confidence":0.0,"reason":"短い根拠"}',
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
  const matchedInsect = category === 'insect'
    ? findInsectCandidate(commonName, scientificName)
    : null;
  const matchedPlant = category === 'plant'
    ? findPlantCandidate(commonName, scientificName)
    : null;
  const rarity = matchedInsect?.rarity
    ?? matchedPlant?.rarity
    ?? normalizeRarity(parsed.rarity)
    ?? (category === 'insect' || category === 'plant' ? 'common' : null);
  const confidence = typeof parsed.confidence === 'number'
    ? Math.max(0, Math.min(1, parsed.confidence))
    : 0.5;
  const reason = typeof parsed.reason === 'string' && parsed.reason.trim()
    ? parsed.reason.trim()
    : '画像AIによる推定です。';

  return {
    category,
    commonName: matchedInsect?.commonName ?? matchedPlant?.commonName ?? commonName,
    scientificName: matchedInsect?.scientificName ?? matchedPlant?.scientificName ?? scientificName,
    rarity,
    confidence,
    reason,
  };
}

function findPlantCandidate(
  commonName: string,
  scientificName: string | null,
): (typeof PLANT_CANDIDATES)[number] | null {
  return PLANT_CANDIDATES.find((candidate) => (
    candidate.commonName === commonName ||
    (scientificName != null && candidate.scientificName === scientificName)
  )) ?? null;
}

function findInsectCandidate(
  commonName: string,
  scientificName: string | null,
): (typeof INSECT_CANDIDATES)[number] | null {
  return INSECT_CANDIDATES.find((candidate) => (
    candidate.commonName === commonName ||
    candidate.scientificName === scientificName
  )) ?? null;
}

function normalizeRarity(value: unknown): RarityValue | null {
  return value === 'common' ||
    value === 'uncommon' ||
    value === 'rare' ||
    value === 'special'
    ? value
    : null;
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
