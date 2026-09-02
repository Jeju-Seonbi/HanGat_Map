export const PLACEHOLDER_IMAGE='/images/placeholder.svg'
export function useFallbackImage(event:Event,fallback=PLACEHOLDER_IMAGE){const image=event.currentTarget as HTMLImageElement;if(image.dataset.fallbackApplied)return;image.dataset.fallbackApplied='true';image.src=fallback}
