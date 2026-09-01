/* 지도 조작 창구.
   KakaoMap.vue가 마운트될 때 실제 구현을 채워 넣고, 목록·검색·코스 등 어느 컴포넌트에서든
   import 해서 지도를 움직인다. 카카오 지도 인스턴스는 크고 반응형일 필요가 없어
   store가 아니라 여기에 둔다 */
export const mapBridge = {
  ready: false,
  panTo() {},
  zoomTo() {},
  fitRegion() {},
  fitPoints() {},
  relayout() {},
}
