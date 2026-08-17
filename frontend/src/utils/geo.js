/** 두 지점 사이 직선거리 (km) */
export function dist(a, b) {
  const R = 6371, p = Math.PI / 180, dy = (b.y - a.y) * p, dx = (b.x - a.x) * p
  const q = Math.sin(dy / 2) ** 2 + Math.cos(a.y * p) * Math.cos(b.y * p) * Math.sin(dx / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(q))
}

/** 대략적인 차량 이동 시간 (분) — 평균 시속 38km 가정 */
export const drive = (a, b) => Math.max(5, Math.round(dist(a, b) / 38 * 60))

export const won = n => n.toLocaleString('ko-KR')

/** CSS 변수 값 읽기 (지도 경로선 색 등 JS에서 필요한 곳) */
export const cssVar = n => getComputedStyle(document.documentElement).getPropertyValue('--' + n).trim()

/** 이미지 축소 — 프로토타입이 localStorage에 사진을 담기 위한 임시 수단.
    실서비스에서는 원본을 파일 스토리지에 올린다 */
export function shrink(file, mx) {
  return new Promise(res => {
    const img = new Image()
    img.onload = () => {
      const k = Math.min(1, mx / img.width), cv = document.createElement('canvas')
      cv.width = Math.round(img.width * k)
      cv.height = Math.round(img.height * k)
      cv.getContext('2d').drawImage(img, 0, 0, cv.width, cv.height)
      URL.revokeObjectURL(img.src)
      res(cv.toDataURL('image/jpeg', 0.72))
    }
    img.src = URL.createObjectURL(file)
  })
}
