export const MAP_LABEL_MAX_LEVEL = 7

export const shouldShowMapLabels = (level: number) => level <= MAP_LABEL_MAX_LEVEL

export const POI_MARKER_CLASS = {
  food: 'mk-food',
  dine: 'mk-dine',
  cafe: 'mk-cafe',
  cvs: 'mk-cvs',
  stay: 'mk-stay',
  mart: 'mk-mart',
} as const
