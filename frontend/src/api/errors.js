/** API 오류 — 실제 HTTP 응답과 같은 모양을 유지해 서버 연동 시 화면 코드를 안 고치게 한다 */
export class ApiError extends Error {
  constructor (status, code, message, detail = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.detail = detail
  }
}
