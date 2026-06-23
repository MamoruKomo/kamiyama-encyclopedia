export type SpeciesCategory = 'plant' | 'insect';

export type Rarity = 'common' | 'uncommon' | 'rare' | 'special';

export type LatLng = {
  latitude: number;
  longitude: number;
};

export type SpeciesCandidate = {
  id: string;
  category: SpeciesCategory;
  commonName: string;
  scientificName: string;
  family: string;
  hint: string;
  seasonMonths: number[];
  rarity: Rarity;
  knownLocations: LatLng[];
  sourceUrl: string;
};

export type NatureZone = {
  id: string;
  name: string;
  kind: 'river' | 'forest' | 'village' | 'ridge' | 'field';
  description: string;
  polygon: LatLng[];
};

export type Observation = {
  id: string;
  photoUri: string;
  category: SpeciesCategory;
  candidateId: string | null;
  customName: string;
  note: string;
  latitude: number;
  longitude: number;
  accuracy: number | null;
  observedAt: string;
  environment: string;
  rarity: Rarity;
  source?: 'web' | 'thinklet';
  externalPhotoUri?: string;
  aiLabel?: string;
  aiConfidence?: number | null;
  aiScientificName?: string | null;
  aiReason?: string;
};
