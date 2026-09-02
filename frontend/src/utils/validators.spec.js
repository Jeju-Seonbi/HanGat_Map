import { describe, it, expect, afterEach } from 'vitest'
import {
  isValidEmail, checkEmail, checkNickname, checkName, maskEmail, normalizeEmail,
  EMAIL_MAX_LENGTH, EMAIL_POLICY, buildEmailRegex, emailRegex,
  EMAIL_INPUT_FILTER, NAME_INPUT_FILTER, NAME_MAX_LENGTH,
  checkBirthDate, todayISODate, BIRTH_MIN_YEAR
} from './validators.js'

const original = { ...EMAIL_POLICY }
afterEach(() => { Object.assign(EMAIL_POLICY, original) })

describe('이메일 — 통과해야 하는 실제 주소', () => {
  it('국내외 주요 제공자 형식을 통과시킨다', () => {
    for (const e of [
      'test@hangat.kr',
      'zh2171@gmail.com',
      'user.name@naver.com',
      'a.b.c@sub.example.co.kr',
      'a@b.co',                 // 최소 길이 6자
      'KIM@NAVER.COM'           // 대문자
    ]) {
      expect(isValidEmail(e), e).toBe(true)
    }
  })

  it('숫자로 시작하는 도메인을 통과시킨다 (RFC 1123 §2.1)', () => {
    // "The restriction on the first character is relaxed to allow either a letter or a digit."
    // 163.com 처럼 실재하는 대형 제공자가 여기 해당한다.
    expect(isValidEmail('user@163.com')).toBe(true)
    expect(isValidEmail('abc@3m.com')).toBe(true)
  })

  it('로컬파트 64자까지 허용한다 (RFC 5321 §4.5.3.1)', () => {
    expect(isValidEmail('a'.repeat(64) + '@x.com')).toBe(true)
    expect(isValidEmail('a'.repeat(65) + '@x.com')).toBe(false)
  })
})

describe('이메일 — 특수문자는 @ 와 . 만 (제품 결정)', () => {
  /*
    ⚠️ 이 규칙은 RFC 5322 · WHATWG · OWASP 가 허용하는 것보다 좁다.
       비용을 실측해 두었다 — validators.js 상단 주석 참고
       (공개 무료 메일 도메인 13,239개 중 1,132개(8.55%)가 하이픈 포함, t-online.de 등).
       넓히려면 EMAIL_POLICY.restrictSpecials = false.
  */
  const blocked = [
    ['user+tag@gmail.com', '+'],          // RFC 5233 서브어드레싱
    ['first_last@company.com', '_'],
    ['first-last@company.com', '-'],
    ['kim@t-online.de', '-'],             // 실재하는 독일 대형 제공자
    ['kim@xn--3e0b707e', '-'],            // 퓨니코드 IDN (.한국)
    ['a!b@x.com', '!'],
    ["o'brian@x.com", "'"],
    ['a`b@x.com', '`'],
    ['a"b@x.com', '"'],
    ['a;b@x.com', ';'],
    ['a,b@x.com', ','],
    ['a<b@x.com', '<'],
    ['a$b@x.com', '$'],
    ['a*b@x.com', '*'],
    ['a|b@x.com', '|']
  ]

  it('영숫자와 @ . 이외의 기호를 전부 막는다', () => {
    for (const [e, ch] of blocked) {
      expect(isValidEmail(e), `${e} (${ch})`).toBe(false)
    }
  })

  it('OWASP 가 "dangerous characters" 로 지목한 문자가 문법에 없다', () => {
    // 백틱·따옴표·NUL — Input Validation Cheat Sheet
    for (const e of ['a`b@x.com', "a'b@x.com", 'a"b@x.com', 'a\0b@x.com']) {
      expect(isValidEmail(e), e).toBe(false)
    }
  })

  it('인젝션 페이로드가 이메일로는 성립할 수 없다', () => {
    for (const e of [
      '"><script>alert(1)</script>"@example.org',   // RFC 5322 상으로는 유효한 주소다
      "' OR '1'='1@x.com",
      'a@x.com;DROP TABLE users',
      '${jndi:ldap://evil.example/a}@x.com',
      'a@x.com\r\nBcc: victim@x.com'                // 메일 헤더 인젝션
    ]) {
      expect(isValidEmail(e), e).toBe(false)
    }
  })

  it('막힌 이유를 구체적으로 알려준다 — 규칙이 좁은 만큼 안내가 필요하다', () => {
    expect(checkEmail('kim@t-online.de').message).toContain('하이픈')
    expect(checkEmail('user+tag@gmail.com').message).toContain('+')
    expect(checkEmail('first_last@x.com').message).toContain('_')
    expect(checkEmail('plain').message).toContain('@')
    expect(checkEmail('a@b.com@c.com').message).toContain('하나')
    expect(checkEmail('a b@x.com').message).toContain('공백')
  })

  it('완화 모드로 되돌리면 다시 통과한다 — 스위치가 실제로 동작한다', () => {
    EMAIL_POLICY.restrictSpecials = false
    for (const e of ['user+tag@gmail.com', 'kim@t-online.de', 'first_last@company.com']) {
      expect(isValidEmail(e), e).toBe(true)
    }
    // 완화해도 공백·개행은 여전히 막힌다
    expect(isValidEmail('a b@x.com')).toBe(false)
  })
})

describe('이메일 — 점(.) 위치 규칙 (dot-atom)', () => {
  it('점은 글자 사이에만, 연달아 쓸 수 없다', () => {
    for (const e of ['.a@x.com', 'a.@x.com', 'a..b@x.com', 'a@.x.com', 'a@x..com', 'a@x.com.']) {
      expect(isValidEmail(e), e).toBe(false)
    }
  })
  it('가운데 점은 여러 번 써도 된다', () => {
    expect(isValidEmail('a.b.c.d@x.y.z.com')).toBe(true)
  })
})

describe('이메일 — 형식·길이', () => {
  it('형식이 아닌 값을 막는다', () => {
    for (const v of ['', 'test', 'test@', '@hangat.kr', 'a b@x.com', 'a@b', 'a@b.', 'a@b.c']) {
      expect(isValidEmail(v), JSON.stringify(v)).toBe(false)
    }
  })

  it('맨 뒤 라벨은 영문이어야 한다 — IP 형태를 막는다 (RFC 1123 §2.1)', () => {
    // "A valid host name can never have the dotted-decimal form #.#.#.#"
    expect(isValidEmail('user@1.2.3.4')).toBe(false)
    expect(isValidEmail('user@192.168.0.1')).toBe(false)
  })

  it('254자를 넘으면 막는다 (RFC 5321 + Erratum 1690)', () => {
    expect(EMAIL_MAX_LENGTH).toBe(254)
    expect(isValidEmail('a'.repeat(EMAIL_MAX_LENGTH) + '@x.com')).toBe(false)
    // 경계값: 정확히 254자.
    // 도메인은 라벨 63자 상한(RFC 1034)을 지켜 쪼개야 한다 — 한 덩어리 185자는 규칙 위반이다.
    const local = 'a'.repeat(64)
    const domain = ['b'.repeat(63), 'b'.repeat(63), 'b'.repeat(57), 'com'].join('.')
    const at254 = `${local}@${domain}`
    expect(domain.length).toBe(254 - 64 - 1)
    expect(at254.length).toBe(254)
    expect(isValidEmail(at254)).toBe(true)
    expect(isValidEmail(`a${at254}`)).toBe(false)   // 255자
  })

  it('도메인 라벨 63자 상한을 정규식이 직접 강제한다 (RFC 1034 §3.5)', () => {
    expect(isValidEmail('a@' + 'x'.repeat(63) + '.com')).toBe(true)
    expect(isValidEmail('a@' + 'x'.repeat(64) + '.com')).toBe(false)
  })

  it('앞뒤 공백은 다듬어서 받는다 (붙여넣기 사고 방지)', () => {
    expect(isValidEmail('  test@hangat.kr \n')).toBe(true)
  })
})

describe('이메일 정규식 자체', () => {
  it('길이 제한까지 정규식 한 줄에 들어 있다 — 서버로 그대로 복사할 수 있다', () => {
    const { source } = buildEmailRegex(EMAIL_POLICY)
    expect(source).toContain('{6,254}')
    expect(source).toContain('{1,64}@')
    expect(source).toContain('{1,63}')
  })

  it('`$` 대신 `(?![\s\S])` 로 끝을 잠근다 — 끝 줄바꿈이 통과하면 안 된다', () => {
    // JS·Java 모두 `$` 는 문자열 맨 끝 줄바꿈 앞에서 매칭된다.
    // 이 정규식은 서버로 복사해 쓰라고 만든 것이라 정규식 자체가 정확해야 한다.
    const { regex, source } = buildEmailRegex(EMAIL_POLICY)
    expect(source.endsWith('$')).toBe(false)
    expect(regex.test('a@x.com')).toBe(true)
    expect(regex.test('a@x.com\n')).toBe(false)
  })

  it('정책값을 바꾸면 emailRegex() 도 따라 바뀐다 (캐시가 굳지 않는다)', () => {
    const before = emailRegex().source
    EMAIL_POLICY.restrictSpecials = false
    expect(emailRegex().source).not.toBe(before)
  })

  it('ReDoS — 악성 입력에도 선형 시간이다', () => {
    const evil = 'a'.repeat(120) + '@' + 'a.'.repeat(60) + '!'
    const t0 = Date.now()
    for (let i = 0; i < 2000; i++) isValidEmail(evil)
    const ms = Date.now() - t0
    expect(ms, `2000회 ${ms}ms`).toBeLessThan(200)
  })
})

describe('normalizeEmail', () => {
  it('대소문자·공백을 무시하고 같은 계정으로 본다', () => {
    expect(normalizeEmail('  Test@Hangat.KR ')).toBe('test@hangat.kr')
  })
})

describe('checkName — 문자만 (숫자·특수문자 불가)', () => {
  it('영문·한글·기타 문자 이름을 통과시킨다', () => {
    for (const n of ['홍길동', 'Hong Gildong', '김한갓', 'Ольга', '山田太郎', 'José', '홍 길동']) {
      expect(checkName(n).ok, n).toBe(true)
    }
  })

  it('숫자를 막는다', () => {
    for (const n of ['홍길동2', 'Hong2', '2', '홍 길동 3']) {
      expect(checkName(n).ok, n).toBe(false)
    }
    expect(checkName('홍길동2').message).toContain('숫자')
  })

  it('특수문자를 막는다', () => {
    for (const n of ['홍길동!', 'Hong_Gildong', 'Hong.Gildong', '홍길동@', '<script>', "' OR 1=1"]) {
      expect(checkName(n).ok, n).toBe(false)
    }
  })

  it('⚠️ 실존하는 이름 표기도 막힌다 — 요청("특수문자 불가")의 알려진 비용', () => {
    // 되돌리려면 validators.js 의 NAME_PATTERN 문자 클래스에 `\-'` 를 더하면 된다
    for (const n of ['Anne-Marie', "O'Brian", "D'Angelo", '山田・太郎']) {
      expect(checkName(n).ok, n).toBe(false)
    }
  })

  it('앞뒤 공백은 다듬고, 연속 공백은 막는다', () => {
    expect(checkName('  홍길동  ').ok).toBe(true)
    expect(checkName('홍  길동').ok).toBe(false)
    expect(checkName('홍  길동').message).toContain('공백')
  })

  it('빈 값과 길이 상한을 막는다', () => {
    expect(checkName('').ok).toBe(false)
    expect(checkName('   ').ok).toBe(false)
    expect(checkName('가'.repeat(NAME_MAX_LENGTH)).ok).toBe(true)
    expect(checkName('가'.repeat(NAME_MAX_LENGTH + 1)).ok).toBe(false)
  })
})

describe('입력 필터 (FieldText 용)', () => {
  const strip = (re, s) => { re.lastIndex = 0; return s.replace(re, '') }

  it('이메일 필터는 영숫자와 @ . 만 남긴다', () => {
    expect(strip(EMAIL_INPUT_FILTER, 'ki m@t-online.de!')).toBe('kim@tonline.de')
    expect(strip(EMAIL_INPUT_FILTER, "' OR 1=1@x.com")).toBe('OR11@x.com')
    expect(strip(EMAIL_INPUT_FILTER, '한글@x.com')).toBe('@x.com')
    expect(strip(EMAIL_INPUT_FILTER, 'test@hangat.kr')).toBe('test@hangat.kr')
  })

  it('이름 필터는 문자와 공백만 남긴다', () => {
    expect(strip(NAME_INPUT_FILTER, '홍길동2!')).toBe('홍길동')
    expect(strip(NAME_INPUT_FILTER, '<script>alert(1)</script>')).toBe('scriptalertscript')
    expect(strip(NAME_INPUT_FILTER, 'Hong Gildong')).toBe('Hong Gildong')
    expect(strip(NAME_INPUT_FILTER, 'José')).toBe('José')
  })

  it('필터를 통과한 값은 검증도 통과한다 — 둘이 어긋나면 입력이 불가능해진다', () => {
    // 필터가 남긴 문자를 검증이 다시 막으면 사용자는 영원히 폼을 채울 수 없다
    for (const raw of ['홍길동2!', 'Hong-Gildong', '김@한갓#']) {
      const cleaned = strip(NAME_INPUT_FILTER, raw).replace(/ {2,}/g, ' ').trim()
      if (cleaned) expect(checkName(cleaned).ok, `${raw} → ${cleaned}`).toBe(true)
    }
    for (const raw of ['ki m@t-online.de!', 'test@hangat.kr']) {
      const cleaned = strip(EMAIL_INPUT_FILTER, raw)
      expect(typeof cleaned).toBe('string')
    }
  })

  it('⚠️ 필터는 UX 일 뿐 보안 장치가 아니다 — 검증이 최종 판정이다', () => {
    // DOM 필터를 우회해 값을 직접 넣어도 checkName / checkEmail 이 막는다
    expect(checkName('홍길동2').ok).toBe(false)
    expect(isValidEmail('kim@t-online.de')).toBe(false)
  })
})

describe('checkNickname', () => {
  it('2~20자, 공백 없음', () => {
    expect(checkNickname('한갓이').ok).toBe(true)
    expect(checkNickname('가').ok).toBe(false)
    expect(checkNickname('가'.repeat(21)).ok).toBe(false)
    expect(checkNickname('한갓 이').ok).toBe(false)
    expect(checkNickname('   ').ok).toBe(false)
  })
})

describe('maskEmail (USER_003 — 중간에 ** 표시)', () => {
  it('로컬파트 가운데를 가리고 도메인은 남긴다', () => {
    expect(maskEmail('zh2171@gmail.com')).toBe('zh***1@gmail.com')
    expect(maskEmail('test@hangat.kr')).toBe('te*t@hangat.kr')
  })
  it('짧은 로컬파트는 첫 글자만 남긴다', () => {
    expect(maskEmail('ab@x.com')).toBe('a**@x.com')
    expect(maskEmail('abc@x.com')).toBe('a**@x.com')
  })
  it('@ 가 없으면 그대로 돌려준다', () => {
    expect(maskEmail('not-an-email')).toBe('not-an-email')
  })
  it('원래 로컬파트가 그대로 남지 않는다', () => {
    const masked = maskEmail('zh2171@gmail.com')
    expect(masked.includes('zh2171')).toBe(false)
    expect(masked).toContain('*')
  })
})


describe('생년월일', () => {
  // 시간에 의존하는 테스트가 실행 시각에 따라 흔들리지 않게 기준 시각을 고정한다
  const now = new Date(2026, 7, 15)   // 2026-08-15
  const chk = v => checkBirthDate(v, { now })

  it('빈 값은 통과하고 null 로 정리된다 (선택 항목)', () => {
    for (const v of ['', '   ', null, undefined]) {
      expect(chk(v)).toEqual({ ok: true, message: '', value: null })
    }
  })

  it('정상 날짜를 통과시킨다', () => {
    for (const v of ['1999-04-12', '1900-01-01', '2000-02-29', '2026-08-15']) {
      expect(chk(v).ok, v).toBe(true)
      expect(chk(v).value).toBe(v)
    }
  })

  it('없는 날짜를 막는다', () => {
    // Date 는 이 값들을 조용히 다음 달로 넘긴다 — 그래서 되돌려 비교한다
    for (const v of ['2026-02-31', '2025-02-29', '2026-04-31', '2026-13-01', '2026-00-10']) {
      expect(chk(v).ok, v).toBe(false)
    }
  })

  it('미래 날짜를 막고 오늘은 허용한다', () => {
    expect(chk('2026-08-16').ok).toBe(false)
    expect(chk('2027-01-01').ok).toBe(false)
    expect(chk('2026-08-15').ok).toBe(true)
  })

  it('형식이 다르면 막는다', () => {
    for (const v of ['1999/04/12', '99-04-12', '1999-4-12', '1999-04-12T00:00', 'abc', '19990412']) {
      expect(chk(v).ok, v).toBe(false)
    }
  })

  it('BIRTH_MIN_YEAR 이전을 막는다', () => {
    expect(chk('1899-12-31').ok).toBe(false)
    expect(chk(BIRTH_MIN_YEAR + '-01-01').ok).toBe(true)
  })

  it('나이 하한은 두지 않는다 (정의서에 연령 제한이 없다)', () => {
    expect(chk('2026-01-01').ok).toBe(true)
  })

  it('todayISODate 는 input[type=date] 가 받는 형식을 낸다', () => {
    expect(todayISODate(new Date(2026, 0, 5))).toBe('2026-01-05')
    expect(todayISODate(now)).toBe('2026-08-15')
    expect(chk(todayISODate(now)).ok).toBe(true)
  })
})
