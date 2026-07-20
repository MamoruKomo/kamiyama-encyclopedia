# Current State

Last investigated: 2026-07-20

This document records the current THINKLET biology encyclopedia MVP without treating unverified items as confirmed on-device behavior.

## Product Shape

The current MVP is a three-part flow:

1. A child wears THINKLET and presses a physical button to capture an observation.
2. The THINKLET Android app takes a photo, reads time and location when available, runs ML Kit Image Labeling, compresses the photo, and POSTs the observation to a Cloudflare Worker.
3. The Web/GitHub Pages app or `field-android` app pulls observations from the Worker and shows discoveries in a map and dex.

The core product value is free-form exploration. Preloaded species locations are hints and context, not required spots that users must visit.

## Repository Layout

```text
.
├── src/                         Expo / React Native Web app for GitHub Pages
├── src/components/              Web map, capture, and encyclopedia panels
├── src/data/kamiyama.ts         Web candidate species and map data
├── src/lib/                     Web geospatial helpers, IndexedDB, sync import
├── sync-worker/                 Cloudflare Worker sync API using KV
├── thinklet-android/            THINKLET capture and upload Android app
├── field-android/               Android Studio smartphone viewer app
├── scripts/                     Web build adjustment scripts
├── .github/workflows/deploy.yml GitHub Pages deployment workflow
└── docs/                        Current-state and migration documentation
```

Ignored generated or local files include `node_modules/`, `dist/`, `.expo/`, Android `.gradle/`, `.kotlin/`, `build/`, `.env`, and both Android `local.properties` files.

## THINKLET Android App

Path: `thinklet-android/`

Main files:

- `app/src/main/java/com/mamorukomo/kamiyama/thinklet/MainActivity.kt`
- `app/src/main/java/com/mamorukomo/kamiyama/thinklet/ThinkletObservationViewModel.kt`
- `app/src/main/java/com/mamorukomo/kamiyama/thinklet/ThinkletObservationScreen.kt`
- `app/build.gradle.kts`

### Capture Flow

`ThinkletObservationViewModel.captureObservation()`:

1. Plays a short beep.
2. Captures a JPEG with CameraX `ImageCapture`.
3. Saves the original file under the app external files `observations` directory.
4. Runs ML Kit Image Labeling.
5. Requests location from Android `LocationManager`.
6. Builds a payload with id, category, label, confidence, location, time, local photo URI, compressed base64 image, and MIME type.
7. Sends the payload to the sync API when `sendAfterCapture=true`.

### Physical Button Handling

`MainActivity` forwards `onKeyDown` and `onKeyUp` to `ThinkletObservationViewModel.handleThinkletButton()`.

Recognized key codes include:

- `KEYCODE_STEM_PRIMARY`
- `KEYCODE_STEM_1`
- `KEYCODE_STEM_2`
- `KEYCODE_STEM_3`
- `KEYCODE_CAMERA`
- `KEYCODE_FOCUS`
- `KEYCODE_DPAD_CENTER`
- `KEYCODE_ENTER`
- `KEYCODE_HEADSETHOOK`
- `KEYCODE_BUTTON_A`

On key up, the app captures and syncs when the last accepted press was more than 1500 ms ago.

### ML Kit Dependency

`thinklet-android/app/build.gradle.kts` includes `libs.mlkit.image.labeling`.

The app uses:

- `ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.45f).build())`
- The highest-confidence label only.

Current category mapping is simple:

- Insect words: `insect`, `butterfly`, `bee`, `wasp`, `beetle`, `bug`, `dragonfly`
- Plant words: `plant`, `flower`, `tree`, `leaf`, `grass`, `fern`, `herb`
- Otherwise `unknown`

### Photo Compression

The original photo is saved locally as JPEG. For upload/analysis, the app:

- Decodes bounds first.
- Downsamples to a maximum side of 1280 px.
- Compresses to JPEG quality 82.
- Sends the result as base64 JSON in `photoBase64`.

### Location

The app requests `CAMERA`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, and `INTERNET`.

Location is read from `LocationManager`:

- If fine location is granted: tries GPS, then network.
- If only coarse location is granted: tries network.
- Fresh GPS timeout: 20 seconds.
- Fresh network timeout: 8 seconds.
- Falls back to last known location.

If no permission or no enabled provider exists, latitude/longitude are sent as null.

### Offline Queue

No durable offline upload queue was found. A failed upload only updates UI state and plays an error tone. The latest payload remains in memory and can be manually resent while the app process remains alive, but it is not persisted as a retry queue.

### Duplicate Upload Prevention

The THINKLET payload id is currently generated as `thinklet-${observedAt}`. There is no persistent client-side sent-state table. Re-sending the same in-memory payload uses the same id, but a new capture creates a new id.

## Cloudflare Worker

Path: `sync-worker/`

Main files:

- `sync-worker/src/index.ts`
- `sync-worker/wrangler.jsonc`
- `sync-worker/package.json`

### Bindings And Environment

Current Worker bindings/types:

- `OBSERVATIONS`: KV namespace
- `ALLOWED_ORIGINS`
- `SYNC_WRITE_TOKEN`
- `OPENAI_API_KEY`
- `OPENAI_MODEL`
- `AI_MODE`

`wrangler.jsonc` currently configures:

- Worker name: `kamiyama-encyclopedia-sync`
- Compatibility date: `2026-06-16`
- KV binding: `OBSERVATIONS`
- Allowed origins: GitHub Pages and localhost

Secrets are not stored in committed config. Android `local.properties` and `.env` are ignored. Generated Android build output can contain expanded BuildConfig constants, so `build/` directories should remain ignored and should not be shared.

### Current API List

Current implemented routes:

- `GET /health`
- `POST /observations`
- `GET /observations?since=<millis>`
- `POST /api/v1/observations`
- `GET /api/v1/observations`
- `GET /api/v1/observations/:id/image`
- `PATCH /api/v1/observations/:id`
- `GET /api/v1/devices/me/sync-status`
- `GET /api/v1/public/observations`
- `GET /api/v1/public/observations/:id`
- `GET /api/v1/public/observations/:id/image`
- `GET /api/v1/public/species`
- `GET /api/v1/public/map`
- `GET /api/v1/review/observations`
- `GET /api/v1/review/observations/:id`
- `POST /api/v1/review/observations/:id/confirm`
- `POST /api/v1/review/observations/:id/reject`
- `POST /api/v1/review/observations/:id/reclassify`
- `OPTIONS *`

The v1 route `POST /api/v1/observations` is implemented for multipart uploads. Phase 10 public and review routes are also implemented. List-style v1 routes support `cursor`, `limit`, `status`, `from`, `to`, `species_id`, and `bbox=minLon,minLat,maxLon,maxLat` where applicable.

Public routes return confirmed observations only. They return rounded `public_latitude` / `public_longitude` and do not expose `device_id`, exact `latitude`, or exact `longitude`.

### Upload Format

`POST /observations` currently expects JSON, not multipart form data.

Important fields accepted by type:

- `id`
- `source`
- `category`
- `label`
- `confidence`
- `aiLabel`
- `aiConfidence`
- `latitude`
- `longitude`
- `accuracyMeters`
- `observedAt`
- `photoUri`
- `photoBase64`
- `photoMimeType`
- `photoDataUrl`

Authorization:

- If `SYNC_WRITE_TOKEN` is unset, writes are open.
- If set, the request must include `Authorization: Bearer <token>`.

### KV Key Design

Observation records:

- Key: `obs:${id}`
- Value: normalized JSON payload
- Metadata: `receivedAt`, `observedAt`

Reference image cache:

- Key: `ref-image:${scientificName}`
- Value: GBIF reference image metadata
- TTL: 14 days

Legacy KV still stores old observation JSON and photo data URLs. New v1 uploads store observation records in D1. Images go to R2 when available; production currently stores v1 images in KV under `image:${image_key}` because R2 is disabled for the Cloudflare account.

### List Behavior

`GET /observations` lists up to 1000 KV keys with prefix `obs:`.

If `since` is passed, keys with metadata `receivedAt <= since` are skipped. Results are sorted by `receivedAt` ascending and returned with `serverTime`.

### OpenAI Call

OpenAI is used only when:

- `AI_MODE === "openai"`
- `OPENAI_API_KEY` is present
- A photo data URL exists

The Worker calls `https://api.openai.com/v1/responses` with:

- `model: env.OPENAI_MODEL || DEFAULT_OPENAI_MODEL`
- Input text containing Kamiyama/Tokushima candidate lists.
- The observation image.
- Optional GBIF reference image URLs.

The current default model is hard-coded as `gpt-5.4-mini`.

The response is parsed from output text as JSON. There is no formal JSON Schema validation yet.

### Free Recognition

When OpenAI is disabled or fails, the Worker uses `inferSpeciesFromDeviceSignal()`.

Current species-level free fallback only maps:

- `stag beetle`, `クワガタ`, `鍬形` -> `ノコギリクワガタ`
- `rhinoceros beetle`, `カブト`, `甲虫`, `beetle` -> `カブトムシ`

This means most insects and plants remain category-level or unknown in free mode.

### Candidate Data

Worker candidate lists are embedded in `sync-worker/src/index.ts`.

Current notable candidate pools:

- Insects: 7 species.
- Plants: 5 starter species plus 43 Tokushima plant candidates.

OpenAI mode can use these candidates and GBIF reference images. Free mode does not yet use candidate active months or habitats to produce a top-3 candidate list.

## Web / GitHub Pages App

Path: repository root `src/`

Main files:

- `src/App.tsx`
- `src/components/MapPanel.tsx`
- `src/components/CapturePanel.tsx`
- `src/components/EncyclopediaPanel.tsx`
- `src/lib/storage.ts`
- `src/lib/syncApi.ts`
- `src/lib/thinkletImport.ts`

### Current Features

- Expo / React Native Web app.
- GitHub Pages static export.
- OpenStreetMap via Leaflet.
- Current-location request via `expo-location`.
- Local observations stored in IndexedDB.
- THINKLET URL import compatibility.
- Pulls Worker observations from `/observations?since=...`.
- Stores a sync cursor in `localStorage`.

### API Usage

`src/lib/syncApi.ts` uses:

- Default endpoint: `https://kamiyama-encyclopedia-sync.kamiyama-kmc2314.workers.dev`
- `GET ${endpoint}/observations?since=${cursor}`

The Web app does not use `/api/v1/observations` yet.

## field-android App

Path: `field-android/`

Main files:

- `app/src/main/java/com/mamorukomo/kamiyama/field/MainActivity.kt`
- `app/src/main/java/com/mamorukomo/kamiyama/field/data/SyncClient.kt`
- `app/src/main/java/com/mamorukomo/kamiyama/field/data/ObservationStore.kt`
- `app/src/main/java/com/mamorukomo/kamiyama/field/ui/KamiyamaFieldApp.kt`
- `app/src/main/java/com/mamorukomo/kamiyama/field/ui/screens/MapScreen.kt`
- `app/src/main/java/com/mamorukomo/kamiyama/field/ui/screens/DexScreen.kt`
- `app/src/main/java/com/mamorukomo/kamiyama/field/ui/screens/SyncScreen.kt`

### Current Features

- Native Android app built with Jetpack Compose.
- Screens: `うけとる`, `マップ`, `ずかん`.
- OpenStreetMap through osmdroid.
- Current-location fallback to Kamiyama center.
- Local SQLite storage.
- Pulls observations from Worker and saves new IDs locally.
- Stores last sync time in SharedPreferences.
- Filters out unknown, undetermined, and non-plant/non-insect AI results before saving to the dex.

### Local Database

`ObservationStore` creates SQLite table `observations`:

- `id`
- `photo_uri`
- `category`
- `candidate_id`
- `custom_name`
- `note`
- `latitude`
- `longitude`
- `accuracy`
- `observed_at_millis`
- `environment`
- `rarity`

This is local app storage, not Cloudflare D1.

### API Usage

`SyncClient` uses:

- `GET ${endpoint}/observations`
- `GET ${endpoint}/observations?since=<lastObservationSyncAt>`

It does not use the future `/api/v1` API yet.

## Tests And Verification

Current scripts:

- Root web: `npm run typecheck`, `npm run build`, `npm run dev`
- Worker: `npm run typecheck`, `npm run deploy`, `npm run dev`
- THINKLET Android: `./gradlew assembleDebug`
- field-android: `./gradlew assembleDebug`

No dedicated test files were found during this investigation.

Previously recorded verification in memory indicates:

- Web build/typecheck were used for the Expo portion.
- Worker typecheck and deploy were used.
- field-android was previously built, installed, launched, and inspected on a connected device.

Those previous device checks were not rerun as part of this document update.

## Deploy Paths

Web:

- `.github/workflows/deploy.yml`
- Runs `npm ci`, `npm run build`, then deploys `dist` to GitHub Pages.

Worker:

- `sync-worker/package.json` has `npm run deploy` -> `wrangler deploy`.
- `README.md` documents KV namespace setup and secret setup.

Android:

- `thinklet-android/` and `field-android/` are separate Android Studio projects.
- Local endpoint/token values are supplied through each project `local.properties` or Gradle properties.

## Implemented Migration Work After Initial Investigation

The following migration pieces have now been added:

- D1 migration: `sync-worker/migrations/0001_initial.sql`
- D1 binding in `sync-worker/wrangler.jsonc`
- New Worker upload API: `POST /api/v1/observations`
- New Worker list API: `GET /api/v1/observations`
- New Worker image API: `GET /api/v1/observations/:id/image`
- New Worker compatibility review API: `PATCH /api/v1/observations/:id`
- Phase 10 public APIs under `/api/v1/public/*`
- Phase 10 review APIs under `/api/v1/review/*`
- Phase 10 device sync status API: `GET /api/v1/devices/me/sync-status`
- Phase 11 admin metrics API: `GET /api/v1/admin/metrics`
- Phase 11 structured Worker logs for upload, duplicate, rejection, classification, confirmation, and request failures.
- Phase 11 duplicate-send metric persisted in D1 table `sync_metrics`.
- Phase 12 Worker test suite using Node's built-in test runner and mocked D1/KV.
- R2 image storage for new v1 uploads when R2 is enabled; current production uses KV image fallback because the Cloudflare account has R2 disabled.
- SHA-256 calculation and D1 persistence.
- Duplicate `(device_id, client_observation_id)` handling.
- `SpeciesClassifier` interface with `FreeRuleClassifier` and `OpenAIClassifier`.
- Human confirmation before `confirmed`.
- Web/PWA candidate review tab.
- field-android v1 confirmed-observation sync with legacy fallback.
- THINKLET v1 multipart upload with legacy fallback and pending queue.
- KV migration dry-run script: `sync-worker/scripts/kv-to-d1-r2-dry-run.mjs`

Verification completed:

- `sync-worker`: `npm run typecheck`
- `sync-worker`: local D1 migration apply
- `sync-worker`: local D1 table/index inspection
- `sync-worker`: `npx wrangler deploy --dry-run`
- `sync-worker`: remote D1 database creation and migration
- `sync-worker`: production Worker deploy with D1 + KV image fallback
- `sync-worker`: production v1 upload -> candidate_ready -> confirm -> image fetch smoke test, followed by cleanup of the smoke observation/image
- `sync-worker`: Web/PWA candidate confirmation is public for MVP; management review actions still require `SYNC_WRITE_TOKEN`.
- `sync-worker`: 2026-07-21 production smoke confirmed no-token `confirm` succeeds and no-token `reject` returns 401.
- `sync-worker`: Phase 10 API implementation passed local typecheck, dry-run, and production deploy on 2026-07-21. Deployed Worker version: `7f14b744-aeac-4e8e-8489-e6f9996ad7f8`.
- `sync-worker`: Phase 10 production smoke uploaded a beetle-labelled test JPEG, saw `candidate_ready` with `trypoxylus-dichotomus` and `prosopocoilus-inclinatus`, confirmed `trypoxylus-dichotomus`, verified public detail/map/image routes, then rejected the smoke observation to remove it from public results.
- `sync-worker`: Public detail smoke returned `public_latitude` / `public_longitude` only and did not expose `device_id`, exact `latitude`, or exact `longitude`.
- `sync-worker`: `GET /api/v1/devices/me/sync-status` returns 401 without a bearer token and returns device counters with a valid token.
- `sync-worker`: 2026-07-21 local `npm test` passes. Tests cover unauthenticated upload, invalid image bytes, oversized image, missing location, free beetle classification, candidate confirmation, duplicate upload, public location redaction, D1 insert failure cleanup, R2 put failure, OpenAI failure fallback, and admin metrics auth/counters.
- `sync-worker`: Phase 11/12 Worker deploy succeeded on 2026-07-21. Deployed Worker version: `a71e4681-e648-4d24-8f00-3bde464fc1be`.
- `sync-worker`: Production `GET /api/v1/admin/metrics` returns 401 without a bearer token and returns status/device counters with a valid token.
- `sync-worker`: 2026-07-21 D1 migration `0002_sync_metrics.sql` applied to local and remote D1. Later Worker deploy version: `1c6ef66c-4ad4-4664-ba1a-e8046732e162`.
- `sync-worker`: Production duplicate-send smoke created one duplicate upload, confirmed `duplicate_send_count` incremented to `1`, then removed the smoke observation row and KV image. The duplicate counter is intentionally retained as an aggregate.
- Android SDK: `npm run android:doctor` confirms both Android projects point to `/Users/mamoru/Library/Android/sdk`, but that path is missing `platforms` or `platform-tools`.
- Web/PWA: `npm run typecheck`
- Web/PWA: `npm run build`

Verification not completed:

- THINKLET Android build: blocked because `thinklet-android/local.properties` points to a missing Android SDK path.
- field-android build: same SDK availability issue applies.
- Real Cloudflare R2 deploy: blocked until R2 is enabled in the Cloudflare Dashboard.
- Real THINKLET device capture/upload test.
- Full Phase 12 Android/Web test automation is not implemented yet.

## Remaining Gaps Against Target Architecture

1. R2 is still not active in production because the Cloudflare account returned code 10042: R2 must be enabled through the dashboard.
2. The production Worker currently uses D1 plus KV image fallback for v1 uploads.
3. The `species` table exists and is seeded opportunistically by the Worker, but candidate species are still also embedded in Worker/Web/Android code.
4. Old KV `/observations` compatibility remains intentionally active.
5. Old KV observation records have not been migrated yet; only the dry-run helper exists.
6. Classification runs in `ctx.waitUntil`, not a durable queue. A production retry queue would be safer for long-running AI work.
7. Android builds and real THINKLET testing are blocked until the local Android SDK path is fixed.
8. Phase 12 currently covers Worker API behavior first; THINKLET button/queue tests and Web UI tests still need dedicated harnesses.
