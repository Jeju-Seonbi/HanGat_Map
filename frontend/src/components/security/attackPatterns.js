/**
 * 공격 문자열(페이로드) 탐지.
 *
 * ─────────────────────────────────────────────────────────────
 * ⚠️ 먼저 읽을 것 — 이 모듈은 "차단 목록(denylist)"이다
 * ─────────────────────────────────────────────────────────────
 * OWASP Input Validation Cheat Sheet 는 이 접근을 명시적으로 경고한다.
 *
 *   "It is a common mistake to use denylist validation in order to try to detect
 *    possibly dangerous characters and patterns like the apostrophe ' character,
 *    the string 1=1, or the <script> tag, but this is a massively flawed approach
 *    as it is trivial for an attacker to bypass such filters."
 *   https://cheatsheetseries.owasp.org/cheatsheets/Input_Validation_Cheat_Sheet.html
 *
 * OWASP Authentication Cheat Sheet 는 비밀번호에 대해 더 직접적이다.
 *
 *   "Allow usage of all characters including unicode and whitespace.
 *    There should be no password composition rules limiting the type of
 *    characters permitted."
 *   https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html
 *
 * 즉 **이 모듈은 인젝션 방어가 아니다.** 인젝션은 파라미터 바인딩으로 막고,
 * 비밀번호는 어차피 해시되어 원문이 SQL 근처에 가지 않는다(CWE-89 참고).
 *
 * 그럼에도 켜 둔 이유는 셋이다. 방어력이 아니라 **운영상의 이유**다.
 *   ① 공격 페이로드를 자기 비밀번호로 쓰는 사용자는 사실상 없다 → 실사용 비용이 낮다
 *   ② WAF·프록시가 이런 문자열을 담은 요청을 통째로 떨궈서,
 *      "가입은 됐는데 로그인이 안 되는" 계정이 만들어지는 사고를 막는다
 *   ③ 페이로드가 로그·모니터링 화면에 그대로 흘러 들어가는 것을 줄인다
 *
 * **이 목록은 우회 가능하고, 완전하지도 않다.** 그 사실을 감추지 않는다.
 * 끄려면 PASSWORD_POLICY.blockAttackPatterns 를 false 로 두면 된다.
 *
 * ─────────────────────────────────────────────────────────────
 * ReDoS
 * ─────────────────────────────────────────────────────────────
 * 아래 패턴에는 중첩 수량자 `(a+)+` 가 없고, 무제한 `.*` 도 쓰지 않는다.
 * 추가로 scan() 이 길이를 먼저 잘라내(SCAN_LIMIT) 긴 입력이 패턴에 닿지 않게 한다.
 */

/** 패턴을 돌리기 전에 잘라내는 길이. 비밀번호 상한(128)보다 넉넉하게 잡았다. */
export const SCAN_LIMIT = 512

/**
 * 값(value) 조각 — 따옴표로 감싼 문자열 또는 맨 단어.
 * 항등식 판정에 쓴다. `[^'"]*` 는 부정 문자셋이라 백트래킹이 선형이다.
 */
const QUOTED = String.raw`(?:'[^']*'|"[^"]*")`

export const ATTACK_PATTERNS = [
  {
    key: 'sqlTautology',
    label: 'SQL 항등식',
    /*
      `OR '1'='1'` `OR ""=""` `OR 1=1` 형태만 잡는다.
      **양변이 모두 따옴표이거나 모두 숫자일 때만** 걸리게 좁혔다.
      이렇게 하지 않으면 "salt and pepper = life" 같은 평범한 문장이 걸린다.
    */
    re: new RegExp(
      String.raw`\b(?:or|and)\b\s*(?:` +
      QUOTED + String.raw`\s*(?:=|<>|!=)\s*` + QUOTED +
      String.raw`|\d{1,6}\s*(?:=|<>|!=)\s*\d{1,6})`,
      'i'
    )
  },
  {
    key: 'sqlKeyword',
    label: 'SQL 명령어',
    /*
      단어 하나만으로는 걸지 않는다. `select` 나 `from` 은 영어 문장에 흔하다.
      실제 공격에서만 붙어 나오는 **두 단어 조합·전용 함수**만 넣었다.
      → "I select the blue one from three" 는 통과한다 (테스트로 고정).
    */
    re: /\b(?:union\s+(?:all\s+)?select|drop\s+(?:table|database)\b|truncate\s+table\b|insert\s+into\b|delete\s+from\b|update\s+\w{1,32}\s+set\b|alter\s+table\b|information_schema\b|xp_cmdshell\b|sp_executesql\b|load_file\s*\(|into\s+(?:out|dump)file\b|benchmark\s*\(|pg_sleep\s*\(|waitfor\s+delay\b|sleep\s*\(\s*\d)/i
  },
  {
    key: 'sqlComment',
    label: 'SQL 주석 종결자',
    /*
      `--` 는 **앞이 문자열 시작이거나 공백·구분기호일 때만** 본다.
      그래야 `Bank-Of-Jeju--2026` 같은 정상 비밀번호가 걸리지 않는다 (테스트로 고정).
    */
    re: /(?:^|[\s;'"`)\]])--(?:[\s-]|$)|\/\*|\*\//
  },
  {
    key: 'jndiLookup',
    label: 'JNDI/Log4Shell 룩업',
    re: /\$\{\s*(?:jndi|env|sys|ctx|lower|upper|date|script|java|spring)\s*:/i
  },
  {
    key: 'nosqlOperator',
    label: 'NoSQL 연산자 주입',
    /* {"$ne": null} · {'$gt': ''} · {$where: ...} */
    re: /["']\s*\$(?:ne|eq|gt|gte|lt|lte|in|nin|or|and|not|nor|where|regex|expr|exists|type|function|jsonSchema)\s*["']?\s*:|\{\s*\$(?:where|function|expr)\b/i
  },
  {
    key: 'ldapFilter',
    label: 'LDAP 필터 주입',
    /* *)(uid=* · )(|( · (&( — LDAP 필터 문법의 괄호·논리연산자 조합 */
    re: /\*\s*\)\s*\(|\)\s*\(\s*[|&]|\(\s*[|&]\s*\(/
  },
  {
    key: 'htmlScript',
    label: 'HTML/스크립트 주입',
    re: /<\s*\/?\s*(?:script|iframe|img|svg|object|embed|body|style|meta|link|form|input|details|marquee|base)\b|javascript\s*:|data\s*:\s*text\/html|\bon(?:error|load|click|focus|toggle|mouseover|animationstart)\s*=/i
  },
  {
    key: 'pathTraversal',
    label: '경로 탈출',
    re: /(?:\.\.[/\\]){2,}|\.\.[/\\]\.\.|%2e%2e(?:%2f|%5c|[/\\])|(?:^|[/\\])(?:etc[/\\]passwd|proc[/\\]self|windows[/\\]win\.ini)/i
  },
  {
    key: 'commandInjection',
    label: '명령 주입',
    re: /\$\(|`[^`]{1,64}`|\|\s*(?:nc|bash|sh|zsh|curl|wget|python|perl|ruby)\b|;\s*(?:rm|cat|ls|curl|wget|nc|chmod)\s+-?\w|&&\s*(?:rm|cat|curl|wget)\b|\bpowershell(?:\.exe)?\s+-/i
  },
  {
    key: 'templateInjection',
    label: '템플릿 주입',
    /* {{7*7}} · ${...} · <%= %> · #{...} — 안쪽은 부정 문자셋이라 선형이다 */
    re: /\{\{[^{}]{0,64}\}\}|\$\{[^{}]{0,64}\}|<%[=@#-]?[^%]{0,64}%>|#\{[^{}]{0,64}\}/
  },
  {
    key: 'encodedQuote',
    label: '인코딩된 따옴표',
    /*
      디코딩 후 검사(decodeVariants)로도 대부분 잡히지만,
      디코딩이 실패하는 깨진 인코딩까지 덮으려고 원문 표지도 따로 본다.
    */
    re: /%27|%22|\\x2[27]|\\u002[27]|&#x?0*(?:22|27|34|39);/i
  }
]

/**
 * 인코딩을 벗겨 낸 변형들을 만든다.
 *
 * 왜 필요한가 — `%27%20OR%201%3D1%20--` 는 원문만 보면 알파벳과 숫자뿐이라
 * 항등식 패턴에 걸리지 않는다. 한 겹 디코딩하면 `' OR 1=1 --` 가 된다.
 * 서버·프록시가 디코딩한 뒤의 값을 쓰는 경우를 가정한 것이다.
 *
 * ⚠️ 한 겹만 벗긴다. 다중 인코딩(`%2527`)은 잡지 못한다. 한계로 남긴다.
 */
export function decodeVariants (input) {
  const s = String(input ?? '').slice(0, SCAN_LIMIT)
  const out = [s]
  const push = v => { if (v !== s && !out.includes(v)) out.push(v) }

  // 퍼센트 인코딩
  if (s.includes('%')) {
    try {
      push(decodeURIComponent(s))
    } catch {
      // 깨진 시퀀스(`50%off` 등)에서 decodeURIComponent 는 throw 한다.
      // 그 경우 올바른 2자리 16진수만 골라서 바꾼다.
      push(s.replace(/%([0-9a-f]{2})/gi, (_, h) => String.fromCharCode(parseInt(h, 16))))
    }
  }

  // 소스코드식 이스케이프 (\x27, ')
  if (/\\[xu]/i.test(s)) {
    push(s
      .replace(/\\x([0-9a-f]{2})/gi, (_, h) => String.fromCharCode(parseInt(h, 16)))
      .replace(/\\u([0-9a-f]{4})/gi, (_, h) => String.fromCharCode(parseInt(h, 16))))
  }

  // HTML 엔티티 (&#39; &#x27;)
  if (s.includes('&#')) {
    push(s
      .replace(/&#x([0-9a-f]{1,6});?/gi, (_, h) => String.fromCodePoint(parseInt(h, 16)))
      .replace(/&#(\d{1,7});?/g, (_, d) => String.fromCodePoint(Number(d))))
  }

  return out
}

/**
 * 공격 페이로드 여부를 본다. 원문과 디코딩 변형을 모두 훑는다.
 * @param {string} input
 * @returns {{hit:boolean, key:string|null, label:string|null, variant:string|null}}
 */
export function detectAttackPattern (input) {
  const variants = decodeVariants(input)
  for (const p of ATTACK_PATTERNS) {
    for (const v of variants) {
      if (p.re.test(v)) {
        return { hit: true, key: p.key, label: p.label, variant: v }
      }
    }
  }
  return { hit: false, key: null, label: null, variant: null }
}

/** 편의용 불리언 */
export function hasAttackPattern (input) {
  return detectAttackPattern(input).hit
}
