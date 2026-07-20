CREATE TABLE IF NOT EXISTS observations (
  id TEXT PRIMARY KEY,
  client_observation_id TEXT NOT NULL,
  device_id TEXT NOT NULL,
  captured_at TEXT NOT NULL,
  received_at TEXT NOT NULL,
  latitude REAL CHECK (latitude IS NULL OR (latitude >= -90 AND latitude <= 90)),
  longitude REAL CHECK (longitude IS NULL OR (longitude >= -180 AND longitude <= 180)),
  public_latitude REAL CHECK (public_latitude IS NULL OR (public_latitude >= -90 AND public_latitude <= 90)),
  public_longitude REAL CHECK (public_longitude IS NULL OR (public_longitude >= -180 AND public_longitude <= 180)),
  location_accuracy_m REAL CHECK (location_accuracy_m IS NULL OR location_accuracy_m >= 0),
  location_visibility TEXT NOT NULL DEFAULT 'public_rounded' CHECK (
    location_visibility IN ('private', 'public_rounded', 'public_exact')
  ),
  image_key TEXT NOT NULL,
  image_sha256 TEXT NOT NULL,
  ml_labels_json TEXT NOT NULL DEFAULT '[]',
  broad_category TEXT CHECK (
    broad_category IS NULL OR broad_category IN (
      'insect',
      'butterfly',
      'beetle',
      'plant',
      'flower',
      'tree',
      'mushroom',
      'unknown'
    )
  ),
  candidate_species_json TEXT,
  confirmed_species_id TEXT,
  status TEXT NOT NULL DEFAULT 'uploaded' CHECK (
    status IN (
      'uploaded',
      'classifying',
      'candidate_ready',
      'needs_review',
      'needs_retake',
      'confirmed',
      'rejected',
      'classification_failed'
    )
  ),
  classifier_mode TEXT CHECK (classifier_mode IS NULL OR classifier_mode IN ('free', 'openai')),
  classifier_version TEXT,
  quality_score REAL CHECK (quality_score IS NULL OR (quality_score >= 0 AND quality_score <= 1)),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  UNIQUE (device_id, client_observation_id),
  FOREIGN KEY (confirmed_species_id) REFERENCES species(id)
) STRICT;

CREATE TABLE IF NOT EXISTS species (
  id TEXT PRIMARY KEY,
  japanese_name TEXT NOT NULL,
  scientific_name TEXT NOT NULL UNIQUE,
  category TEXT NOT NULL CHECK (category IN ('plant', 'insect')),
  description TEXT NOT NULL DEFAULT '',
  active_months_json TEXT NOT NULL DEFAULT '[]',
  habitat_tags_json TEXT NOT NULL DEFAULT '[]',
  image_url TEXT,
  is_sensitive_location INTEGER NOT NULL DEFAULT 0 CHECK (is_sensitive_location IN (0, 1)),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
) STRICT;

CREATE INDEX IF NOT EXISTS idx_observations_captured_at
  ON observations (captured_at);

CREATE INDEX IF NOT EXISTS idx_observations_status
  ON observations (status);

CREATE INDEX IF NOT EXISTS idx_observations_confirmed_species_id
  ON observations (confirmed_species_id);

CREATE INDEX IF NOT EXISTS idx_observations_updated_at
  ON observations (updated_at);

CREATE INDEX IF NOT EXISTS idx_species_category
  ON species (category);
