export const colors = {
  ink: '#17382B',
  inkSoft: '#496157',
  forest: '#2F6B45',
  forestDark: '#1E4D34',
  moss: '#5E8B58',
  leaf: '#79A96B',
  leafPale: '#EAF3DF',
  mint: '#F3F8EF',
  paper: '#FCFCF8',
  white: '#FFFFFF',
  line: '#DDE5DC',
  lineStrong: '#C9D5C9',
  amber: '#F2A62B',
  amberPale: '#FFF1D2',
  blue: '#4D85C5',
  bluePale: '#E8F1FB',
  danger: '#B85547',
  shadow: 'rgba(28, 58, 42, 0.10)',
} as const;

export const radius = {
  small: 6,
  medium: 8,
} as const;

export const shadow = {
  boxShadow: `0 6px 18px ${colors.shadow}`,
} as const;
