# Improvement Plan

Last updated: 2026-07-20

This plan keeps the current THINKLET -> Worker -> Web/field-android flow working while moving toward R2, D1, and a safer candidate-confirmation model.

## Guiding Rules

- Do not remove the existing `/observations` API until Web and `field-android` have moved to the new API.
- Do not delete existing KV observation data during the first migration phases.
- Do not commit secrets, API keys, Cloudflare tokens, or generated BuildConfig output.
- Do not claim device verification unless the app was actually installed and checked on a device in that phase.
- Treat AI results as suggestions. A species should become `confirmed` only after human confirmation.
- Prefer Web/PWA for the MVP confirmation experience; keep `field-android` compatible.

## Phase 1: Documentation And Baseline

Status: completed.

Deliverables:

- `docs/CURRENT_STATE.md`
- `docs/IMPROVEMENT_PLAN.md`
- No behavioral code changes.

Verification:

- Repository inspection only.
- No build or device verification required for this documentation-only step.

Exit criteria:

- Current capture, sync, storage, AI, and app API usage are documented.
- Gaps against the target D1/R2 architecture are listed.

## Phase 2: D1 Schema Added Without Switching Runtime

Status: implemented and applied to remote Cloudflare D1.

Goal:

Add Cloudflare D1 migrations while the existing KV runtime continues to work.

Deliverables:

- Add `sync-worker/migrations/0001_initial.sql`.
- Add D1 binding to `sync-worker/wrangler.jsonc`, but keep code paths behind a feature flag until verified.
- Create `observations` table with:
  - `id`
  - `client_observation_id`
  - `device_id`
  - `captured_at`
  - `received_at`
  - `latitude`
  - `longitude`
  - `public_latitude`
  - `public_longitude`
  - `location_accuracy_m`
  - `location_visibility`
  - `image_key`
  - `image_sha256`
  - `ml_labels_json`
  - `broad_category`
  - `candidate_species_json`
  - `confirmed_species_id`
  - `status`
  - `classifier_mode`
  - `classifier_version`
  - `quality_score`
  - `created_at`
  - `updated_at`
- Create `species` table with:
  - `id`
  - `japanese_name`
  - `scientific_name`
  - `category`
  - `description`
  - `active_months_json`
  - `habitat_tags_json`
  - `image_url`
  - `is_sensitive_location`
  - `created_at`
  - `updated_at`
- Add constraints:
  - Primary key on `id`.
  - Unique `(device_id, client_observation_id)`.
  - `status` check constraint.
  - latitude/longitude range checks.
  - `STRICT` tables if compatible with D1.
- Add indexes:
  - `captured_at`
  - `status`
  - `confirmed_species_id`

Verification:

- `cd sync-worker && npm run typecheck`
- `wrangler d1 migrations apply <db> --local`
- Run a local SQL smoke check for table/index existence.

Exit criteria:

- D1 schema exists and is documented.
- No production upload behavior changes yet.

## Phase 3: Classifier Interface And Non-Confirming Results

Status: implemented in Worker.

Goal:

Separate classification from upload/storage and stop treating ML Kit-only labels as confirmed species.

Deliverables:

- Add `SpeciesClassifier` interface in Worker code.
- Add `FreeRuleClassifier`.
- Add `OpenAIClassifier`.
- Define `ClassificationResult`:

```json
{
  "broad_category": "butterfly",
  "candidates": [
    {
      "species_id": "species-id",
      "reason": "ML Kitラベル、季節、生息環境から候補にした理由"
    }
  ],
  "requires_human_confirmation": true,
  "needs_retake": false,
  "classifier_mode": "free",
  "classifier_version": "version"
}
```

Free mode behavior:

- Use ML Kit labels only for broad categories such as `insect`, `butterfly`, `beetle`, `plant`, `flower`, `tree`, `mushroom`, and `unknown`.
- Use candidate species, active months, and habitat tags to suggest up to 3 candidates.
- Do not auto-confirm species from ML Kit labels.
- If no candidate can be made, set `needs_review`.

OpenAI mode behavior:

- Read model name from `OPENAI_MODEL`.
- Validate output with a JSON Schema or strict runtime validator.
- Return only candidates that exist in the `species` table.
- Store AI confidence as a signal, not a probability.

Compatibility:

- Keep old `aiAnalysis` fields available for current Web and `field-android` while adding the new candidate fields.
- Do not put `needs_review` or `needs_retake` observations into the dex by default.

Verification:

- Worker typecheck.
- Unit-style tests or script-based fixtures for ML labels:
  - beetle
  - butterfly
  - bee
  - plant
  - unknown
- Confirm that no free-mode fixture becomes `confirmed`.

Exit criteria:

- Upload and classification responsibilities are separated.
- Current clients still pull observations.

## Phase 4: R2 Image Storage For New Uploads

Status: implemented in Worker for the new v1 API. Production currently uses KV image fallback because the Cloudflare account has R2 disabled.

Goal:

Store newly uploaded images in R2 instead of embedding image data in KV/D1.

Deliverables:

- Add R2 binding.
- Add max upload size constant/env variable.
- Accept JPEG and WebP only.
- Inspect file magic bytes where possible:
  - JPEG: `FF D8 FF`
  - WebP: `RIFF....WEBP`
- Compute SHA-256.
- Store object at:

```text
observations/{device_id}/{client_observation_id}.jpg
```

- Save `image_key` and `image_sha256` to D1.
- Avoid re-saving R2 object for duplicate `(device_id, client_observation_id)`.
- Consider orphan cleanup if D1 insert fails after R2 put.

Compatibility:

- Existing KV observations remain readable.
- Old JSON uploads can continue temporarily.

Verification:

- Worker typecheck.
- Local or staging upload of a small JPEG.
- Duplicate upload returns the existing record and does not create another R2 object.
- Oversized and invalid image payloads are rejected.

Exit criteria:

- New image uploads are stored in R2 in staging or production behind a controlled path.

## Phase 5: New Upload API

Status: implemented, deployed, and smoke-tested with legacy API retained.

Goal:

Implement the target upload contract while retaining the legacy endpoint during migration.

New endpoint:

- `POST /api/v1/observations`
- `multipart/form-data`

Fields:

- `image`
- `client_observation_id`
- `device_id`
- `captured_at`
- `latitude`
- `longitude`
- `location_accuracy_m`
- `ml_labels_json`
- `quality_score`
- `app_version`

Response:

```json
{
  "observation_id": "uuid",
  "client_observation_id": "uuid",
  "status": "uploaded",
  "duplicate": false,
  "accepted_at": "ISO8601"
}
```

Duplicate response behavior:

- Same `(device_id, client_observation_id)` returns HTTP 200 with the existing record.
- `duplicate: true`.
- No new image object is stored.

Important behavior:

- Do not wait for OpenAI to finish before returning success to THINKLET.
- Upload succeeds once R2 and D1 persistence succeeds.

Verification:

- Worker typecheck.
- Multipart upload smoke test.
- Duplicate upload smoke test.
- Unauthorized write test when `SYNC_WRITE_TOKEN` is set.

Exit criteria:

- THINKLET can use `/api/v1/observations` without waiting for classification.
- Legacy `/observations` still works for existing apps until migration is complete.

## Phase 6: THINKLET Client Migration

Status: implemented in code. Android build/device verification is blocked by missing local Android SDK path. `npm run android:doctor` documents the SDK issue.

Goal:

Update THINKLET to use the new API and become resilient in the field.

Deliverables:

- Generate a durable `client_observation_id` per capture.
- Add a stable `device_id`.
- Send multipart image upload to `/api/v1/observations`.
- Send ML labels as JSON, not only the top label.
- Persist an offline queue of unsent observations.
- Mark successfully accepted observations as synced.
- Do not resend synced observations.
- Keep button capture and audio feedback.

Verification:

- `cd thinklet-android && ./gradlew assembleDebug`
- If a THINKLET is connected: install, capture once, verify upload.
- Airplane-mode or network-failure test for queue behavior if practical.

Exit criteria:

- THINKLET upload no longer depends on Web launch or in-memory retry only.

## Phase 7: Web/PWA Confirmation UI

Status: implemented and web build verified.

Goal:

Make the child-facing app handle candidate review clearly and delightfully.

Deliverables:

- Add a review inbox for observations with `candidate_ready` or `needs_review`.
- Show up to 3 candidate cards with photo, name, rarity, and a short reason.
- Add actions:
  - Confirm candidate.
  - Mark as unsure.
  - Reject/retake.
- Add confirmed observations to the dex.
- Keep text light and child-friendly.
- Preserve map/dex exploration feel.

Verification:

- `npm run typecheck`
- `npm run build`
- Browser check for mobile layout.

Exit criteria:

- Unconfirmed AI guesses no longer silently become dex entries.
- Children can complete the discovery loop from photo to confirmed dex card.

## Phase 8: field-android Compatibility Update

Status: implemented in code. Android build/device verification is blocked by missing local Android SDK path. `npm run android:doctor` documents the SDK issue.

Goal:

Keep the Android Studio viewer working while Web/PWA remains primary.

Deliverables:

- Update `SyncClient` to read the new list/detail API when available.
- Preserve old `/observations` fallback until deprecated.
- Show confirmed observations in dex.
- Optionally show candidate-ready observations in a simple review screen.

Verification:

- `cd field-android && ./gradlew assembleDebug`
- `cd field-android && ./gradlew lintDebug` if lint config remains stable.
- Device install and UI inspection when a device is available.

Exit criteria:

- Existing field app users are not broken by D1/R2 migration.

## Phase 9: KV Migration

Status: dry-run helper implemented. Real historical KV migration is not run.

Goal:

Move old KV observations into D1/R2 without data loss.

Deliverables:

- Migration script with `--dry-run`.
- Read KV keys with `obs:` prefix.
- For records with embedded `photoDataUrl`, upload to R2 and write D1 rows.
- Preserve original IDs and timestamps where possible.
- Record skipped or malformed entries.
- Do not delete KV data during the first migration run.

Verification:

- Dry-run report.
- Small controlled migration.
- Compare counts between KV and D1.

Exit criteria:

- Historical discoveries remain visible after clients switch to D1-backed APIs.

## Phase 10: API Surface

Status: implemented in Worker and Web/PWA integration updated. Production Worker deploy still needs to be refreshed after this phase's final verification.

Goal:

Expose the target public, device, and review API shape while keeping legacy compatibility during migration.

Deliverables:

- Public API:
  - `GET /api/v1/public/observations`
  - `GET /api/v1/public/observations/:id`
  - `GET /api/v1/public/species`
  - `GET /api/v1/public/map`
- Device API:
  - `POST /api/v1/observations`
  - `GET /api/v1/devices/me/sync-status`
- Review API:
  - `GET /api/v1/review/observations`
  - `GET /api/v1/review/observations/:id`
  - `POST /api/v1/review/observations/:id/confirm`
  - `POST /api/v1/review/observations/:id/reject`
  - `POST /api/v1/review/observations/:id/reclassify`
- Cursor pagination and filters:
  - `cursor`
  - `limit`
  - `status`
  - `from` / `to`
  - `species_id`
  - `bbox=minLon,minLat,maxLon,maxLat`
- Public routes return only confirmed observations and public rounded coordinates.
- Public routes do not return `device_id`, exact `latitude`, or exact `longitude`.
- Web/PWA now pulls confirmed observations from `/api/v1/public/observations` first.
- Web/PWA review inbox now uses `/api/v1/review/observations` and `/confirm`.

Compatibility:

- Legacy JSON `POST /observations` and `GET /observations` remain available until historical KV migration and Android verification are complete.
- The older `GET /api/v1/observations` and `PATCH /api/v1/observations/:id` remain available for compatibility.
- Cleanup/deprecation is deferred until R2 is enabled and client migration is verified.

Verification:

- Worker typecheck.
- Web typecheck.
- Web build.
- Worker dry-run/deploy.
- Public API smoke test confirming exact location and `device_id` are not returned.
- Review API smoke test.

Exit criteria:

- The official Phase 10 API routes respond in production.
- Public API shows confirmed discoveries only.
- Review API can confirm a candidate without placing unconfirmed observations in the public dex.
- Exact location is not exposed through public API.

## Later Cleanup And Deprecation

Status: deferred.

Goal:

Remove temporary compatibility only after all clients are migrated and verified.

Deferred until:

- R2 is enabled in the Cloudflare account.
- Historical KV migration has been dry-run and then run safely.
- THINKLET and `field-android` debug APKs build on a local Android SDK.
- Device checks confirm both Android apps still work.
