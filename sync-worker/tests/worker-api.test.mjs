import assert from 'node:assert/strict';
import test from 'node:test';

const worker = (await import('../.tmp-tests/index.js')).default;

test('rejects unauthenticated v1 upload', async () => {
  const env = makeEnv();
  const response = await worker.fetch(
    new Request('https://worker.test/api/v1/observations', { method: 'POST' }),
    env,
    makeCtx(),
  );
  assert.equal(response.status, 401);
});

test('rejects unsupported image bytes before storing', async () => {
  const env = makeEnv();
  const form = new FormData();
  form.append('image', new Blob([new Uint8Array([1, 2, 3, 4])], { type: 'image/jpeg' }), 'bad.jpg');
  form.append('device_id', 'device-a');
  form.append('client_observation_id', 'client-a');

  const response = await worker.fetch(
    new Request('https://worker.test/api/v1/observations', {
      method: 'POST',
      headers: { authorization: 'Bearer test-token' },
      body: form,
    }),
    env,
    makeCtx(),
  );

  assert.equal(response.status, 415);
  assert.equal(env.OBSERVATION_DB.rows.size, 0);
  assert.equal(env.OBSERVATIONS.store.size, 0);
});

test('uploads, classifies, confirms, and hides exact location in public API', async () => {
  const env = makeEnv();
  const ctx = makeCtx();
  const form = new FormData();
  form.append('image', new Blob([jpegBytes()], { type: 'image/jpeg' }), 'beetle.jpg');
  form.append('device_id', 'thinklet-1');
  form.append('client_observation_id', 'capture-1');
  form.append('captured_at', '2026-07-20T10:00:00.000Z');
  form.append('latitude', '33.967654');
  form.append('longitude', '134.350345');
  form.append('location_accuracy_m', '8');
  form.append('ml_labels_json', JSON.stringify([
    { text: 'rhinoceros beetle', confidence: 0.91 },
    { text: 'beetle', confidence: 0.84 },
  ]));
  form.append('quality_score', '0.9');

  const upload = await worker.fetch(
    new Request('https://worker.test/api/v1/observations', {
      method: 'POST',
      headers: { authorization: 'Bearer test-token' },
      body: form,
    }),
    env,
    ctx,
  );
  assert.equal(upload.status, 201);
  const uploaded = await upload.json();
  assert.equal(uploaded.duplicate, false);

  await ctx.flush();
  const review = await worker.fetch(
    new Request('https://worker.test/api/v1/review/observations?status=candidate_ready'),
    env,
    makeCtx(),
  );
  assert.equal(review.status, 200);
  const reviewJson = await review.json();
  assert.equal(reviewJson.observations.length, 1);
  assert.equal(reviewJson.observations[0].status, 'candidate_ready');
  assert.equal(reviewJson.observations[0].candidates[0].species_id, 'trypoxylus-dichotomus');

  const confirm = await worker.fetch(
    new Request(`https://worker.test/api/v1/review/observations/${uploaded.observation_id}/confirm`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ species_id: 'trypoxylus-dichotomus' }),
    }),
    env,
    makeCtx(),
  );
  assert.equal(confirm.status, 200);

  const publicDetail = await worker.fetch(
    new Request(`https://worker.test/api/v1/public/observations/${uploaded.observation_id}`),
    env,
    makeCtx(),
  );
  assert.equal(publicDetail.status, 200);
  const publicJson = await publicDetail.json();
  assert.equal(publicJson.observation.public_latitude, 33.968);
  assert.equal(publicJson.observation.public_longitude, 134.35);
  assert.equal('latitude' in publicJson.observation, false);
  assert.equal('longitude' in publicJson.observation, false);
  assert.equal('device_id' in publicJson.observation, false);

  const duplicateForm = new FormData();
  duplicateForm.append('image', new Blob([jpegBytes()], { type: 'image/jpeg' }), 'beetle.jpg');
  duplicateForm.append('device_id', 'thinklet-1');
  duplicateForm.append('client_observation_id', 'capture-1');
  const duplicate = await worker.fetch(
    new Request('https://worker.test/api/v1/observations', {
      method: 'POST',
      headers: { authorization: 'Bearer test-token' },
      body: duplicateForm,
    }),
    env,
    makeCtx(),
  );
  assert.equal(duplicate.status, 200);
  assert.equal((await duplicate.json()).duplicate, true);
});

test('admin metrics requires auth and returns status counters', async () => {
  const env = makeEnv();
  env.OBSERVATION_DB.rows.set('obs-1', makeRow({
    id: 'obs-1',
    status: 'confirmed',
    device_id: 'device-a',
  }));

  const unauthorized = await worker.fetch(
    new Request('https://worker.test/api/v1/admin/metrics'),
    env,
    makeCtx(),
  );
  assert.equal(unauthorized.status, 401);

  const authorized = await worker.fetch(
    new Request('https://worker.test/api/v1/admin/metrics', {
      headers: { authorization: 'Bearer test-token' },
    }),
    env,
    makeCtx(),
  );
  assert.equal(authorized.status, 200);
  const json = await authorized.json();
  assert.equal(json.confirmed_count, 1);
  assert.equal(json.devices[0].device_id, 'device-a');
});

function makeEnv() {
  return {
    OBSERVATIONS: new MockKv(),
    OBSERVATION_DB: new MockD1(),
    SYNC_WRITE_TOKEN: 'test-token',
    AI_MODE: 'free',
    MAX_UPLOAD_BYTES: '1024',
    ALLOWED_ORIGINS: 'https://worker.test',
  };
}

function makeCtx() {
  const tasks = [];
  return {
    waitUntil(task) {
      tasks.push(Promise.resolve(task));
    },
    async flush() {
      await Promise.all(tasks);
    },
    passThroughOnException() {},
  };
}

function jpegBytes() {
  return new Uint8Array([0xff, 0xd8, 0xff, 0xdb, 0x00, 0x43, 0x00, 0xff, 0xd9]);
}

function makeRow(overrides = {}) {
  return {
    id: 'obs-id',
    client_observation_id: 'client-id',
    device_id: 'device-id',
    captured_at: '2026-07-20T10:00:00.000Z',
    received_at: '2026-07-20T10:00:01.000Z',
    latitude: 33.967654,
    longitude: 134.350345,
    public_latitude: 33.968,
    public_longitude: 134.35,
    location_accuracy_m: 8,
    location_visibility: 'public_rounded',
    image_key: 'observations/device-id/client-id.jpg',
    image_sha256: 'sha',
    ml_labels_json: '[]',
    broad_category: null,
    candidate_species_json: null,
    confirmed_species_id: null,
    status: 'uploaded',
    classifier_mode: null,
    classifier_version: null,
    quality_score: 0.9,
    created_at: '2026-07-20T10:00:01.000Z',
    updated_at: '2026-07-20T10:00:01.000Z',
    ...overrides,
  };
}

class MockKv {
  constructor() {
    this.store = new Map();
    this.metadata = new Map();
  }

  async put(key, value, options = {}) {
    this.store.set(key, value);
    this.metadata.set(key, options.metadata ?? null);
  }

  async get(key) {
    return this.store.get(key) ?? null;
  }

  async getWithMetadata(key) {
    return {
      value: this.store.get(key) ?? null,
      metadata: this.metadata.get(key) ?? null,
    };
  }

  async delete(key) {
    this.store.delete(key);
    this.metadata.delete(key);
  }

  async list() {
    return { keys: [] };
  }
}

class MockD1 {
  constructor() {
    this.rows = new Map();
    this.species = new Map();
  }

  prepare(sql) {
    return new MockStatement(this, sql);
  }

  async batch(statements) {
    return await Promise.all(statements.map((statement) => statement.run()));
  }
}

class MockStatement {
  constructor(db, sql) {
    this.db = db;
    this.sql = sql;
    this.args = [];
  }

  bind(...args) {
    this.args = args;
    return this;
  }

  async first() {
    const sql = normalizeSql(this.sql);
    if (sql.includes('where device_id = ? and client_observation_id = ?')) {
      return [...this.db.rows.values()].find((row) => (
        row.device_id === this.args[0] && row.client_observation_id === this.args[1]
      )) ?? null;
    }
    if (sql.includes("where id = ? and status = 'confirmed'")) {
      const row = this.db.rows.get(this.args[0]);
      return row?.status === 'confirmed' ? row : null;
    }
    if (sql.includes('select image_key from observations where id = ?')) {
      const row = this.db.rows.get(this.args[0]);
      return row ? { image_key: row.image_key } : null;
    }
    if (sql.includes('select * from observations where id = ?')) {
      return this.db.rows.get(this.args[0]) ?? null;
    }
    if (sql.includes('select * from species where id = ?')) {
      return this.db.species.get(this.args[0]) ?? null;
    }
    if (sql.includes('count(*) as total') && sql.includes('where device_id = ?')) {
      const rows = [...this.db.rows.values()].filter((row) => row.device_id === this.args[0]);
      return {
        total: rows.length,
        last_received_at: rows.at(-1)?.received_at ?? null,
        pending_count: rows.filter((row) => row.status === 'uploaded' || row.status === 'classifying').length,
        review_count: rows.filter((row) => row.status === 'candidate_ready' || row.status === 'needs_review').length,
        confirmed_count: rows.filter((row) => row.status === 'confirmed').length,
        failed_count: rows.filter((row) => row.status === 'classification_failed').length,
      };
    }
    return null;
  }

  async all() {
    const sql = normalizeSql(this.sql);
    if (sql.includes('from observations')) {
      if (sql.includes('group by status')) {
        const counts = new Map();
        for (const row of this.db.rows.values()) {
          counts.set(row.status, (counts.get(row.status) ?? 0) + 1);
        }
        return { results: [...counts].map(([status, count]) => ({ status, count })) };
      }
      if (sql.includes('group by device_id')) {
        const byDevice = new Map();
        for (const row of this.db.rows.values()) {
          const previous = byDevice.get(row.device_id);
          byDevice.set(row.device_id, {
            device_id: row.device_id,
            total: (previous?.total ?? 0) + 1,
            last_received_at: row.received_at,
          });
        }
        return { results: [...byDevice.values()] };
      }
      let rows = [...this.db.rows.values()];
      if (sql.includes("status = 'confirmed'") || this.args.includes('confirmed')) {
        rows = rows.filter((row) => row.status === 'confirmed');
      }
      if (this.args.includes('candidate_ready')) {
        rows = rows.filter((row) => row.status === 'candidate_ready');
      }
      return { results: rows };
    }
    if (sql.includes('from species')) {
      if (sql.includes('where id in')) {
        return { results: this.args.map((id) => this.db.species.get(id)).filter(Boolean) };
      }
      return { results: [...this.db.species.values()] };
    }
    return { results: [] };
  }

  async run() {
    const sql = normalizeSql(this.sql);
    if (sql.startsWith('insert into observations')) {
      const row = makeRow({
        id: this.args[0],
        client_observation_id: this.args[1],
        device_id: this.args[2],
        captured_at: this.args[3],
        received_at: this.args[4],
        latitude: this.args[5],
        longitude: this.args[6],
        public_latitude: this.args[7],
        public_longitude: this.args[8],
        location_accuracy_m: this.args[9],
        location_visibility: this.args[10],
        image_key: this.args[11],
        image_sha256: this.args[12],
        ml_labels_json: this.args[13],
        broad_category: this.args[14],
        candidate_species_json: this.args[15],
        confirmed_species_id: this.args[16],
        status: this.args[17],
        classifier_mode: this.args[18],
        classifier_version: this.args[19],
        quality_score: this.args[20],
        created_at: this.args[21],
        updated_at: this.args[22],
      });
      this.db.rows.set(row.id, row);
      return { success: true };
    }
    if (sql.startsWith('insert or ignore into species')) {
      if (!this.db.species.has(this.args[0])) {
        this.db.species.set(this.args[0], {
          id: this.args[0],
          japanese_name: this.args[1],
          scientific_name: this.args[2],
          category: this.args[3],
          description: this.args[4],
          active_months_json: this.args[5],
          habitat_tags_json: this.args[6],
          image_url: this.args[7],
          is_sensitive_location: this.args[8],
          created_at: this.args[9],
          updated_at: this.args[10],
        });
      }
      return { success: true };
    }
    if (sql.includes('set broad_category = ?')) {
      const row = this.db.rows.get(this.args[6]);
      Object.assign(row, {
        broad_category: this.args[0],
        candidate_species_json: this.args[1],
        status: this.args[2],
        classifier_mode: this.args[3],
        classifier_version: this.args[4],
        updated_at: this.args[5],
      });
      return { success: true };
    }
    if (sql.includes("set confirmed_species_id = ?")) {
      const row = this.db.rows.get(this.args[2]);
      Object.assign(row, {
        confirmed_species_id: this.args[0],
        status: 'confirmed',
        updated_at: this.args[1],
      });
      return { success: true };
    }
    if (sql.includes('update observations set status = ?')) {
      const row = this.db.rows.get(this.args[2]);
      Object.assign(row, { status: this.args[0], updated_at: this.args[1] });
      return { success: true };
    }
    return { success: true };
  }
}

function normalizeSql(sql) {
  return sql.toLowerCase().replace(/\s+/g, ' ').trim();
}
