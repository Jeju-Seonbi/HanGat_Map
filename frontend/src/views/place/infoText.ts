/**
 * 장소 상세 운영시간·휴무·이용요금 텍스트를 줄 단위 구조로 편다.
 *
 * TourAPI 원문은 `<br>`로 줄을 나누는데, 적재된 값은 줄바꿈이 남은 것('...19:00\n- 토요일...')과
 * 태그만 벗겨져 한 줄로 붙은 것('- 본관 09:00~18:00- 공공수장고 09:15~17:30※ 매표시간은...')이 섞여 있다.
 * 둘 다 같은 모양으로 보이도록 표식(- 항목, [묶음], ※ 비고)을 기준으로 다시 자른다. 내용은 바꾸지 않는다.
 */
export interface InfoLine {
  text: string
  /** '- '로 시작한 항목 - 점을 찍어 보여준다 */
  bullet: boolean
}

export interface InfoBlock {
  /** '[개인]'처럼 대괄호로 묶인 소제목. 없으면 null */
  title: string | null
  lines: InfoLine[]
}

export interface InfoText {
  blocks: InfoBlock[]
  /** '※'로 시작한 비고 - 본문 아래 작게 */
  notes: string[]
}

const EMPTY: InfoText = { blocks: [], notes: [] }

/**
 * 붙어 버린 표식 앞에 줄바꿈을 넣는다.
 * '- ' 뒤가 숫자면 자르지 않는다 - '10:00 - 18:00' 같은 범위와 '※ 무료 - 6세 이하' 같은 부연은 한 줄이다.
 */
function restoreBreaks (raw: string): string {
  return raw
    .replace(/\r/g, '')
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/\s*※/g, '\n※')
    .replace(/\s*\[(?=[^\]]+\])/g, '\n[')
    .replace(/(?<=\S)\s*-\s+(?=[^\d\s])/g, '\n- ')
}

export function parseInfoText (raw: string | null | undefined): InfoText {
  if (!raw || !raw.trim()) return EMPTY
  const blocks: InfoBlock[] = []
  const notes: string[] = []
  let current: InfoBlock | null = null

  for (const piece of restoreBreaks(raw).split('\n')) {
    const line = piece.trim()
    if (!line) continue
    if (line.startsWith('※')) {
      notes.push(line.replace(/^※\s*/, ''))
      continue
    }
    const heading = /^\[([^\]]+)\]\s*(.*)$/.exec(line)
    if (heading) {
      current = { title: heading[1].trim(), lines: [] }
      blocks.push(current)
      if (heading[2]) current.lines.push({ text: heading[2].trim(), bullet: false })
      continue
    }
    if (!current) {
      current = { title: null, lines: [] }
      blocks.push(current)
    }
    const bullet = /^-\s+/.test(line)
    current.lines.push({ text: line.replace(/^-\s+/, ''), bullet })
  }
  return { blocks, notes }
}

/** 소제목이 둘 이상이거나 줄이 많으면 한 칸에 안 들어간다 - 그리드 한 줄을 통째로 쓴다 */
export function isWideInfo (info: InfoText): boolean {
  const lineCount = info.blocks.reduce((sum, block) => sum + block.lines.length, 0)
  return info.blocks.length > 1 || lineCount > 3
}
