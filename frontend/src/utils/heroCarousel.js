export function nextHeroIndex (currentIndex, total) {
  if (total <= 0) return 0
  return (currentIndex + 1) % total
}
