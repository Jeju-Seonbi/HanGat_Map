import { computed, ref } from 'vue'

/**
 * 테마 3-상태 (시스템 / 라이트 / 다크).
 *
 * - 선택값은 <html data-theme> 로만 표현한다. 값이 없으면 시스템 설정을 따른다.
 * - 첫 페인트 전 적용은 index.html 의 인라인 스크립트가 담당한다 (깜빡임 방지).
 *   여기서는 그 뒤의 변경만 다룬다.
 * - 저장은 localStorage. 민감 정보가 아니므로 저장해도 된다.
 */

const STORAGE_KEY = 'hangat_theme'
export const THEMES = ['system', 'light', 'dark']

function read () {
  try {
    const v = window.localStorage.getItem(STORAGE_KEY)
    return THEMES.includes(v) ? v : 'system'
  } catch {
    return 'system'
  }
}

/** 모듈 전역 — 여러 컴포넌트가 같은 상태를 본다 */
const choice = ref(typeof window === 'undefined' ? 'system' : read())

const media = typeof window !== 'undefined' && window.matchMedia
  ? window.matchMedia('(prefers-color-scheme: dark)')
  : null

const systemDark = ref(media ? media.matches : false)
media?.addEventListener?.('change', e => { systemDark.value = e.matches })

function apply (value) {
  const root = document.documentElement
  if (value === 'system') root.removeAttribute('data-theme')
  else root.setAttribute('data-theme', value)
}

export function useTheme () {
  /** 실제로 화면에 적용 중인 값 */
  const resolved = computed(() =>
    choice.value === 'system' ? (systemDark.value ? 'dark' : 'light') : choice.value
  )

  function setTheme (value) {
    if (!THEMES.includes(value)) return
    choice.value = value
    apply(value)
    try {
      if (value === 'system') window.localStorage.removeItem(STORAGE_KEY)
      else window.localStorage.setItem(STORAGE_KEY, value)
    } catch {
      /* 저장 실패해도 이번 세션에는 적용된다 */
    }
  }

  /** 현재 보이는 것의 반대로 뒤집는다. 시스템 상태에서 누르면 반대 테마로 고정된다. */
  function toggle () {
    setTheme(resolved.value === 'dark' ? 'light' : 'dark')
  }

  return { choice, resolved, systemDark, setTheme, toggle }
}
