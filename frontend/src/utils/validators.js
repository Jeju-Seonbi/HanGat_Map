/**
 * 이메일 · 닉네임 검증과 마스킹.
 *
 * 비밀번호 규칙은 여기 없다 → `src/components/security/passwordPolicy.js`
 * (근거·정책·정의서와의 충돌 설명이 전부 그 파일에 있다)
 *
 * ══════════════════════════════════════════════════════════════
 * 이메일 — 원문 확인한 근거
 * ══════════════════════════════════════════════════════════════
 * RFC 5321 §4.5.3.1 (크기 제한)
 *   · "The local part of a mailbox MUST NOT exceed 64 characters"
 *   · "The domain portion of a mailbox address MUST NOT exceed 255 characters"
 *   · "The maximum total length of a reverse-path or forward-path is 256 characters"
 *   https://datatracker.ietf.org/doc/html/rfc5321#section-4.5.3.1
 *   → path 는 `<`…`>` 를 포함하므로 **주소 자체의 상한은 254자**다.
 *     RFC 3696 Erratum 1690 이 이 점을 정정했다. https://errata.rfc-editor.org/eid1690/
 *
 * RFC 1034 §3.5 (도메인 라벨)
 *   · 라벨은 "start with a letter, end with a letter or digit, and have as interior
 *     characters only letters, digits, and hyphen", 최대 63자
 *   https://datatracker.ietf.org/doc/html/rfc1034#section-3.5
 * RFC 1123 §2.1 (완화)
 *   · "The restriction on the first character is relaxed to allow either a letter or a digit."
 *   · "A valid host name can never have the dotted-decimal form #.#.#.#, since at least
 *     the highest-level component label will be alphabetic."
 *   https://datatracker.ietf.org/doc/html/rfc1123#section-2
 *   → 숫자로 시작하는 도메인이 실재한다(163.com). 그래서 첫 글자를 영문으로 제한하지 않았고,
 *     최상위 라벨(TLD)만 영문으로 강제한다.
 *
 * WHATWG HTML — "valid e-mail address"
 *   /^[a-zA-Z0-9.!#$%&'*+\/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?
 *    (?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$/
 *   스펙 스스로 "willful violation of RFC 5322" 라고 밝힌다. RFC 5322 문법이
 *   "too strict (before the "@"), too vague (after the "@"), and too lax" 라서다.
 *   https://html.spec.whatwg.org/multipage/input.html#valid-e-mail-address
 *
 * OWASP Input Validation Cheat Sheet
 *   · "The email address contains two parts, separated with an @ symbol."
 *   · "The email address does not contain dangerous characters (such as backticks,
 *      single or double quotes, or null bytes)."
 *   · "The domain part contains only letters, numbers, hyphens (-) and periods (.)."
 *   · "properly parsing email addresses for validity with regular expressions is very
 *      complicated" → 최종 판정은 **실제로 메일이 도착하는지**(인증 코드)로 한다.
 *   https://cheatsheetseries.owasp.org/cheatsheets/Input_Validation_Cheat_Sheet.html
 *
 * ══════════════════════════════════════════════════════════════
 * 제품 결정: 특수문자는 `@` 와 `.` 만 허용
 * ══════════════════════════════════════════════════════════════
 * 위 표준들은 모두 이보다 넓은 문자를 허용한다. 좁힌 건 **제품 결정**이다.
 * 얻는 것과 잃는 것을 숨기지 않고 적는다.
 *
 * [얻는 것]
 *   · 이메일 값에 인젝션 페이로드가 성립할 수 없다.
 *     따옴표·괄호·세미콜론·백틱·`<`·`>`·`$`·`{`·`}`·`*`·`|` 가 전부 문법에서 빠진다.
 *     (근본 방어는 여전히 파라미터 바인딩·출력 이스케이프다. 이건 보조선이다)
 *   · OWASP 가 "dangerous characters" 로 지목한 백틱·따옴표·NUL 이 원천 차단된다.
 *   · 메일 헤더 인젝션의 재료인 `,` `;` `<` `>` 도 함께 빠진다.
 *
 * [잃는 것 — 실측]
 *   · **하이픈이 든 도메인이 전부 거부된다.**
 *     공개된 무료 메일 도메인 목록 13,239개 중 **1,132개(8.55%)가 하이픈을 포함**한다.
 *     (Kikobeats/free-email-domains, 2026-08 기준 직접 내려받아 계산)
 *     여기에는 독일 최대 메일 제공자 중 하나인 **t-online.de** 가 포함된다.
 *     → 이 계정을 쓰는 사람은 가입 자체가 불가능하다.
 *   · 로컬파트의 `+`(RFC 5233 서브어드레싱), `_`, `-` 가 거부된다.
 *     `user+tag@gmail.com` 같은 주소를 쓰는 사람은 다른 주소를 써야 한다.
 *     사용 비율은 우리 쪽 데이터가 없어 **모른다**. 추정하지 않는다.
 *   · 퓨니코드 IDN(`xn--…`)은 하이픈이 필수라 전부 거부된다. `.한국` 도메인도 마찬가지다.
 *   · 국내 주요 제공자(naver.com, daum.net, hanmail.net, kakao.com, nate.com, gmail.com)는
 *     전부 하이픈이 없어 **영향받지 않는다** — 위 목록에서 확인.
 *
 * 넓은 규칙(WHATWG 기반)으로 되돌리려면 `EMAIL_POLICY.restrictSpecials` 를 false 로
 * 바꾸면 된다. 두 모드 모두 테스트가 있다 (validators.spec.js).
 */

export const EMAIL_POLICY = {
  /** 주소 전체 상한 (RFC 5321 §4.5.3.1 + Erratum 1690) */
  maxLength: 254,
  /** 로컬파트 상한 (RFC 5321 §4.5.3.1) */
  maxLocalLength: 64,
  /** 도메인 라벨 상한 (RFC 1034 §3.5) */
  maxLabelLength: 63,
  /** 최소 길이 — `a@b.co` 가 6자 */
  minLength: 6,
  /**
   * true  → 특수문자는 `@` 와 `.` 만 허용 (제품 결정, 위 주석 참고)
   * false → WHATWG "valid e-mail address" 기준 (하이픈·`+`·`_` 등 허용)
   */
  restrictSpecials: true
}

/* ────────────────────── 정책 → 정규식 ────────────────────── */

/**
 * 현재 정책을 **정규식 한 줄**로 만든다.
 * 서버(Spring `@Pattern`, DB CHECK 제약)로 문자열 그대로 옮길 수 있다.
 *
 * 엄격 모드 (restrictSpecials: true)
 *   ^(?=[\s\S]{6,254}(?![\s\S]))(?=[^@]{1,64}@)
 *    [A-Za-z0-9]+(?:\.[A-Za-z0-9]+)*@(?:[A-Za-z0-9]{1,63}\.)+[A-Za-z]{2,63}(?![\s\S])
 *
 * 설계 메모
 *  · 앞의 두 선행 탐색이 **전체 길이**와 **로컬파트 길이**를 본다.
 *    이렇게 해야 길이 제한까지 정규식 한 줄 안에 들어가 서버로 그대로 복사된다.
 *  · 로컬파트 `[A-Za-z0-9]+(?:\.[A-Za-z0-9]+)*`
 *      → 점은 **가운데에만**, 연속 불가. `.a@`, `a.@`, `a..b@` 가 전부 걸러진다.
 *        RFC 5322 도 점을 이런 위치 규칙으로 정의한다(dot-atom).
 *  · 도메인 `(?:[A-Za-z0-9]{1,63}\.)+[A-Za-z]{2,63}`
 *      → 라벨마다 63자 상한(RFC 1034)을 정규식 자체로 강제한다.
 *      → 마지막 라벨(TLD)만 영문 2자 이상. RFC 1123 §2.1 의
 *        "at least the highest-level component label will be alphabetic" 을 따른 것이라
 *        `1.2.3.4` 같은 IP 형태가 걸러진다.
 *      → 하이픈이 빠져 있는 것이 이 모드의 핵심 제약이다(위 [잃는 것] 참고).
 *
 * ⚠️ ReDoS — `(?:[A-Za-z0-9]{1,63}\.)+` 는 반복마다 리터럴 `.` 를 반드시 먹는다.
 *    `.` 위치가 고정이라 분할 방법이 하나뿐이고, 백트래킹이 입력 길이에 선형이다.
 *    `(a+)+` 같은 모호한 중첩 수량자가 아니다. 회귀 테스트로 시간을 고정해 둔다.
 *
 * @returns {{source:string, flags:string, regex:RegExp}}
 */
export function buildEmailRegex (policy = EMAIL_POLICY) {
  const { minLength, maxLength, maxLocalLength, maxLabelLength, restrictSpecials } = policy

  /*
    ⚠️ `$` 를 쓰지 않고 `(?![\s\S])` 로 끝을 잠근다.
    JS 도 Java 도 `$` 는 **문자열 맨 끝의 줄바꿈 앞**에서도 매칭된다.
    그래서 `^…$` 만 쓰면 `a@x.com\n` 이 통과한다(실제로 통과하는 것을 확인했다).
    이 정규식은 서버로 그대로 복사해 쓰라고 만든 것이라, 그쪽에서도 안전해야 한다.
    JS 에는 `\z` 가 없으므로 "뒤에 어떤 문자도 없음" 을 부정 선행 탐색으로 표현한다.
  */
  const END = '(?![\\s\\S])'

  const lenGuard =
    `(?=[\\s\\S]{${minLength},${maxLength}}${END})` +
    `(?=[^@]{1,${maxLocalLength}}@)`

  const local = restrictSpecials
    // 영숫자 덩어리를 점 하나로만 잇는다 (dot-atom)
    ? '[A-Za-z0-9]+(?:\\.[A-Za-z0-9]+)*'
    // WHATWG atext — 스펙 원문 그대로
    : "[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+"

  const domain = restrictSpecials
    ? `(?:[A-Za-z0-9]{1,${maxLabelLength}}\\.)+[A-Za-z]{2,${maxLabelLength}}`
    // WHATWG label — 하이픈은 라벨 가운데에만
    : `[A-Za-z0-9](?:[A-Za-z0-9-]{0,${maxLabelLength - 2}}[A-Za-z0-9])?` +
      `(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,${maxLabelLength - 2}}[A-Za-z0-9])?)*`

  const source = `^${lenGuard}${local}@${domain}${END}`
  return { source, flags: '', regex: new RegExp(source) }
}

let cachedEmail = null
let cachedEmailKey = ''

/** 현재 정책의 이메일 정규식 (정책값이 바뀌면 자동으로 다시 만든다) */
export function emailRegex () {
  const p = EMAIL_POLICY
  const key = [p.minLength, p.maxLength, p.maxLocalLength, p.maxLabelLength, p.restrictSpecials].join('|')
  if (key !== cachedEmailKey) {
    cachedEmail = buildEmailRegex(p)
    cachedEmailKey = key
  }
  return cachedEmail
}

/** 이메일 길이 상한 (RFC 5321 + Erratum 1690) — 기존 호출부 호환용 별칭 */
export const EMAIL_MAX_LENGTH = EMAIL_POLICY.maxLength

/** 엄격 모드에서 허용하지 않는 기호를 사용자에게 알려주기 위한 표 */
const REJECTED_HINTS = [
  { re: /\s/, message: '이메일에 공백은 넣을 수 없어요' },
  { re: /-/, message: '이메일에는 영문·숫자와 @ . 만 쓸 수 있어요 (하이픈 - 은 불가)' },
  { re: /\+/, message: '이메일에는 영문·숫자와 @ . 만 쓸 수 있어요 (+ 는 불가)' },
  { re: /_/, message: '이메일에는 영문·숫자와 @ . 만 쓸 수 있어요 (밑줄 _ 는 불가)' },
  { re: /[^\x20-\x7E]/, message: '이메일은 영문·숫자로만 쓸 수 있어요' }
]

/**
 * 이메일 검사. 왜 막혔는지까지 돌려준다.
 *
 * ⚠️ 형식이 맞다고 **존재하는 주소는 아니다.** 최종 확인은 인증 메일이 한다
 *    (OWASP Input Validation Cheat Sheet — Semantic Validation).
 *
 * @returns {{ok:boolean, message:string}}
 */
export function checkEmail (email) {
  const s = String(email ?? '').trim()
  const p = EMAIL_POLICY

  if (!s) return { ok: false, message: '이메일을 입력해 주세요' }
  if (s.length > p.maxLength) {
    return { ok: false, message: `이메일은 ${p.maxLength}자까지 쓸 수 있어요` }
  }
  if (emailRegex().regex.test(s)) return { ok: true, message: '' }

  // 통과하지 못했으면 이유를 짚어 준다 — 규칙이 좁은 만큼 안내가 없으면 사용자가 헤맨다
  const at = s.indexOf('@')
  if (at < 0) return { ok: false, message: '@ 가 없어요' }
  if (s.indexOf('@', at + 1) >= 0) return { ok: false, message: '@ 는 하나만 쓸 수 있어요' }
  if (at === 0) return { ok: false, message: '@ 앞부분을 입력해 주세요' }
  if (at > p.maxLocalLength) {
    return { ok: false, message: `@ 앞부분은 ${p.maxLocalLength}자까지예요` }
  }
  if (p.restrictSpecials) {
    for (const h of REJECTED_HINTS) {
      if (h.re.test(s)) return { ok: false, message: h.message }
    }
  }
  if (!s.slice(at + 1).includes('.')) return { ok: false, message: '@ 뒤에 . 이 있어야 해요' }
  if (/\.\./.test(s)) return { ok: false, message: '. 을 연달아 쓸 수 없어요' }
  if (/^\.|\.$|\.@|@\./.test(s)) return { ok: false, message: '. 은 글자 사이에만 쓸 수 있어요' }
  if (!/\.[A-Za-z]{2,}$/.test(s)) return { ok: false, message: '맨 뒤 도메인은 영문 2자 이상이어야 해요' }
  return { ok: false, message: '이메일 형식을 확인해 주세요' }
}

export function isValidEmail (email) {
  return checkEmail(email).ok
}

/** 저장·비교용 정규화. 대소문자 구분 없이 한 계정으로 취급한다. */
export function normalizeEmail (email) {
  return String(email || '').trim().toLowerCase()
}

/* ────────────────────── 입력 단계 필터 ────────────────────── */

/**
 * 입력창에서 **타이핑·붙여넣기 즉시 지워 버릴** 문자.
 * FieldText 의 `filter` prop 으로 넘긴다.
 *
 * ⚠️ 보안 장치가 아니다. DOM 필터는 콘솔·확장·자동화로 우회된다.
 *    실제 판정은 checkEmail / checkName / API 계층이 한다. 여기는 **UX** 다.
 *    허용 문자가 좁을수록 "왜 막혔는지 모르는" 시간이 길어지므로, 아예 안 들어가게 한다.
 */
export const EMAIL_INPUT_FILTER = /[^A-Za-z0-9@.]/g
export const EMAIL_INPUT_MESSAGE = '이메일에는 영문·숫자와 @ . 만 쓸 수 있어요'

/**
 * 이름 필터 — 글자와 공백만.
 * `\p{L}` 은 한글·영문·한자·키릴 등 **모든 문자 범주**를 덮는다. 숫자(`\p{N}`)와
 * 기호는 빠진다. `\p{M}` 은 결합 문자(악센트) 라서 함께 허용한다 — 없으면 `é` 가 깨진다.
 */
export const NAME_INPUT_FILTER = /[^\p{L}\p{M} ]/gu
export const NAME_INPUT_MESSAGE = '이름에는 문자만 쓸 수 있어요 (숫자·특수문자 불가)'

/**
 * 이름: 문자만 (영문·한글 등), 숫자·특수문자 불가.
 *
 * 정규식 한 줄:  ^[\p{L}\p{M}]+(?: [\p{L}\p{M}]+)*$   (길이 검사는 별도)
 *   · 공백은 **단어 사이에만** 한 칸. 앞뒤·연속 공백은 걸린다
 *   · `\p{L}` 이라 "홍길동" "Hong Gildong" "Ольга" 모두 통과한다
 *
 * ⚠️ 비용을 밝힌다 — 실존하는 이름 표기가 막힌다.
 *     `Anne-Marie`(하이픈) · `O'Brian`(어깻점) · `D'Angelo` · 일본어 중점 `・`
 *   요구("특수문자 안 됨")를 그대로 적용한 결과다. 넓히려면 아래 문자 클래스에
 *   `\-'` 를 더하면 된다. 되돌리는 자리를 한 군데로 모아 뒀다.
 */
export const NAME_PATTERN = /^[\p{L}\p{M}]+(?: [\p{L}\p{M}]+)*$/u
export const NAME_MIN_LENGTH = 1
export const NAME_MAX_LENGTH = 30

export function checkName (name) {
  const s = String(name ?? '').trim()
  if (!s) return { ok: false, message: '이름을 입력해 주세요' }
  if (s.length > NAME_MAX_LENGTH) {
    return { ok: false, message: `이름은 ${NAME_MAX_LENGTH}자까지 쓸 수 있어요` }
  }
  if (NAME_PATTERN.test(s)) return { ok: true, message: '' }

  // 왜 막혔는지 짚어 준다
  if (/\d/u.test(s)) return { ok: false, message: '이름에 숫자는 넣을 수 없어요' }
  if (/ {2,}/.test(s)) return { ok: false, message: '공백을 연달아 쓸 수 없어요' }
  return { ok: false, message: NAME_INPUT_MESSAGE }
}

/** 닉네임: 2~20자, 공백 불가 (정의서에 길이 명시 없음 → users.nickname VARCHAR(50) 안에서 정함) */
export function checkNickname (nick) {
  const s = String(nick || '').trim()
  if (!s) return { ok: false, message: '닉네임을 입력해 주세요' }
  if (s.length < 2) return { ok: false, message: '닉네임은 2자 이상이어야 해요' }
  if (s.length > 20) return { ok: false, message: '닉네임은 20자까지 쓸 수 있어요' }
  if (/\s/.test(s)) return { ok: false, message: '닉네임에 공백은 넣을 수 없어요' }
  return { ok: true, message: '' }
}

/**
 * 이메일 마스킹 (USER_003 — "중간에 ** 표시").
 * 로컬파트 앞 2글자와 마지막 1글자만 남기고 가운데를 * 로 덮는다.
 *
 *   zh2171@gmail.com -> zh***1@gmail.com
 *   ab@x.com         -> a**@x.com
 *
 * ⚠️ 이건 **표시용**이다. 마스킹된 값이라도
 *    "이 계정이 존재한다"는 사실 자체를 알려주므로,
 *    본인 확인이 끝난 화면에서만 보여준다 (auth.js requestPasswordReset 주석 참고).
 */
export function maskEmail (email) {
  const s = String(email || '').trim()
  const at = s.lastIndexOf('@')
  if (at < 1) return s
  const local = s.slice(0, at)
  const domain = s.slice(at)
  if (local.length <= 3) return local.slice(0, 1) + '**' + domain
  return local.slice(0, 2) + '*'.repeat(local.length - 3) + local.slice(-1) + domain
}

/* ─────────────────────────── 생년월일 ───────────────────────────
   ⚠️ 이전에는 검사가 **아예 없었다.** 가입 API 가 `birthDate || null` 로
      받은 값을 그대로 저장했다. `2026-02-31`, `9999-01-01`, `abc` 도 통과했다.
      마이페이지에서 변경까지 열면서 공용 검사로 올린다.

   · 선택값이다 — 비우면 null 로 저장한다(정의서에 필수 표기 없음)
   · `<input type="date">` 가 내보내는 형식은 항상 `YYYY-MM-DD` 다(WHATWG date state).
     달력 UI 를 못 쓰는 환경에서 직접 타이핑하는 경우가 있어 형식도 직접 확인한다.
   · 존재하지 않는 날짜를 막는다 — Date 는 2026-02-31 을 3월 3일로 **조용히 넘긴다.**
     그래서 파싱 결과를 되돌려 원래 숫자와 같은지 비교한다.
   · 미래 날짜와 1900년 이전을 막는다. 나이 하한은 두지 않는다 —
     정의서에 연령 제한이 없고, 근거 없이 막으면 정상 사용자를 잠근다. */

export const BIRTH_MIN_YEAR = 1900
export const BIRTH_PATTERN = /^\d{4}-\d{2}-\d{2}$/

/** 오늘 날짜를 `YYYY-MM-DD` 로 (input[type=date] 의 max 에 그대로 넣는다) */
export function todayISODate (now = new Date()) {
  const p = n => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${p(now.getMonth() + 1)}-${p(now.getDate())}`
}

export function checkBirthDate (value, { now = new Date() } = {}) {
  const s = String(value ?? '').trim()
  if (!s) return { ok: true, message: '', value: null }   // 선택값

  if (!BIRTH_PATTERN.test(s)) {
    return { ok: false, message: '생년월일은 YYYY-MM-DD 형식으로 넣어주세요', value: null }
  }

  const [y, m, d] = s.split('-').map(Number)
  if (y < BIRTH_MIN_YEAR) {
    return { ok: false, message: `${BIRTH_MIN_YEAR}년 이후만 넣을 수 있어요`, value: null }
  }
  if (m < 1 || m > 12) return { ok: false, message: '월은 1~12 사이여야 해요', value: null }

  // 존재하는 날짜인지: 파싱 결과가 입력값과 같아야 한다 (2026-02-31 → 3/3 으로 넘어감)
  const dt = new Date(y, m - 1, d)
  if (dt.getFullYear() !== y || dt.getMonth() !== m - 1 || dt.getDate() !== d) {
    return { ok: false, message: '없는 날짜예요. 다시 확인해 주세요', value: null }
  }

  // 미래 금지 — 오늘 자정 기준으로 비교해 "오늘"은 허용한다
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  if (dt > today) {
    return { ok: false, message: '미래 날짜는 넣을 수 없어요', value: null }
  }

  return { ok: true, message: '', value: s }
}
