import type { ImageSourcePropType } from 'react-native';

export const speciesImages: Record<string, ImageSourcePropType> = {
  'trypoxylus-dichotomus': require('../../assets/species/kabutomushi.jpg'),
  'prosopocoilus-inclinatus': require('../../assets/species/nokogiri-kuwagata.jpg'),
  'ardisia-crenata': require('../../assets/species/manryo.jpg'),
  'argynnis-hyperbius': require('../../assets/species/tsumaguro.jpg'),
};

export const featuredSpeciesIds = [
  'argynnis-hyperbius',
  'ardisia-crenata',
  'trypoxylus-dichotomus',
  'prosopocoilus-inclinatus',
];
