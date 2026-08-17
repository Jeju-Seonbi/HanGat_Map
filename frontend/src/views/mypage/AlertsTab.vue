<script setup>
/**
 * 예보 변경 알림 (요구사항 정의서 MY_008).
 *
 * **알림 역할만 한다.** 저장 시점 예보와 최신 예보를 나란히 보여주고 끝이다.
 *  - 카드를 누르면 읽음 처리된다 (다른 화면으로 이동하지 않는다)
 *  - 오른쪽 위 X 로 닫는다
 *  - 보관: 만들어진 지 일주일 안이거나 여행이 안 끝났으면 남는다 (mypage.js isAlertLive)
 *
 * ⚠️ 2026-08-15 — 카드 안의 버튼 3종을 **뺐다**
 *    (`일정 보기` · `이 날짜만 다시 짜기` · `읽음으로`).
 *    그래서 MY_008 의 **부분 재구성이 화면에서 닿을 수 없게 됐다.**
 *    API(`regenerateAffectedDay`)와 그 테스트는 그대로 살아 있다 —
 *    코스 상세 같은 다른 자리에 붙이면 바로 다시 쓸 수 있다.
 *    요구사항이 사라진 게 아니라 진입점이 없어진 상태라는 걸 적어 둔다.
 */
import { computed, onMounted, ref } from 'vue'
import WeatherBadge from '../../components/common/WeatherBadge.vue'
import EmptyState from '../../components/common/EmptyState.vue'
import StateBlock from '../../components/common/StateBlock.vue'
import { listAlerts, setAlertRead, dismissAlert, ALERT_RETENTION_DAYS } from '../../api/mypage.js'
import { useUiStore } from '../../stores/ui.js'
import { useApiError } from '../../composables/useApiError.js'
import { fmt, fmtDateTime, fmtRelative } from '../../utils/format.js'

const ui = useUiStore()
const toMessage = useApiError()

const loading = ref(true)
const error = ref(null)
const data = ref({ items: [], total: 0, unread: 0 })

async function fetchList () {
  loading.value = data.value.items.length === 0
  error.value = null
  try {
    data.value = await listAlerts()
  } catch (e) {
    const msg = toMessage(e)
    if (msg) error.value = msg
  } finally {
    loading.value = false
  }
}
onMounted(fetchList)

const hasUnread = computed(() => data.value.unread > 0)

/** 카드를 누르면 읽음 처리. 이미 읽었으면 아무 일도 하지 않는다 */
async function markRead (a) {
  if (a.read) return
  a.read = true                     // 눌린 느낌을 먼저 준다
  data.value.unread = Math.max(0, data.value.unread - 1)
  try {
    await setAlertRead(a.alertId, true)
    ui.bumpAlerts()
  } catch (e) {
    a.read = false                  // 실패하면 되돌린다
    data.value.unread += 1
    const msg = toMessage(e)
    if (msg) ui.toast(msg)
  }
}

async function close (a) {
  try {
    await dismissAlert(a.alertId)
    await fetchList()
    ui.bumpAlerts()
  } catch (e) {
    const msg = toMessage(e)
    if (msg) ui.toast(msg)
  }
}
</script>

<template>
  <section>
    <div class="bar-top">
      <div class="sect">
        예보 변경 알림
        <span class="cnt tnum">{{ data.total }}</span>
        <span v-if="hasUnread" class="unread tnum">안 읽음 {{ data.unread }}</span>
      </div>
    </div>

    <p class="note lead">
      저장 시점 예보와 최신 예보를 비교해요.
      알림은 {{ ALERT_RETENTION_DAYS }}일 동안, 여행이 더 남았다면 끝날 때까지 남아 있어요.
    </p>

    <StateBlock :loading="loading" :error="error" :rows="3" @retry="fetchList" />

    <template v-if="!loading && !error">
      <EmptyState
        v-if="!data.items.length"
        title="받은 알림이 없어요"
        hint="코스를 저장해 두면 예보가 나빠질 때 여기로 알려드려요."
      />

      <ul v-else class="list">
        <li v-for="a in data.items" :key="a.alertId">
          <article class="card" :class="{ unread: !a.read, low: a.severity === 'LOW' }">
            <!-- 카드 전체가 읽음 처리 버튼이다. X 는 그 위에 따로 얹는다 -->
            <button
              class="hit"
              type="button"
              :aria-label="a.read ? `${a.courseName} 알림 (읽음)` : `${a.courseName} 알림 — 눌러서 읽음 처리`"
              :disabled="a.read"
              @click="markRead(a)"
            >
              <div class="top">
                <span class="dot" :class="a.read ? 'read' : 'new'" aria-hidden="true" />
                <span class="cname">{{ a.courseName }}</span>
                <span class="when note">{{ fmtRelative(a.createdAt) }}</span>
              </div>

              <p class="line">
                <b>{{ fmt(a.affectedDate) }}</b> ·
                {{ a.places.length ? a.places.join(' · ') : '해당 날짜 전체' }}
              </p>

              <div class="cmp">
                <div class="side">
                  <div class="lbl">저장 시점 예보</div>
                  <WeatherBadge :kind="a.before.kind" :t="a.before.t" :size="16" />
                  <p class="note">{{ a.before.note }}</p>
                </div>
                <div class="arw" aria-hidden="true">→</div>
                <div class="side now">
                  <div class="lbl">최신 예보</div>
                  <WeatherBadge :kind="a.after.kind" :t="a.after.t" :size="16" />
                  <p class="note warn">{{ a.after.warning || a.after.note }}</p>
                </div>
              </div>

              <p class="note stamp">알림 생성 {{ fmtDateTime(a.createdAt) }}</p>
            </button>

            <button class="x" type="button" :aria-label="`${a.courseName} 알림 닫기`" @click="close(a)">
              <svg width="14" height="14" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M6 6l12 12M18 6L6 18" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" />
              </svg>
            </button>
          </article>
        </li>
      </ul>
    </template>
  </section>
</template>

<style scoped>
.bar-top { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; flex-wrap: wrap; }
.bar-top .sect { margin: 0; flex: 1; }
.cnt { color: var(--tx3); font-weight: 700; }
.unread { color: var(--busy); font-weight: 800; font-size: 11.5px; }
.lead { margin-bottom: 14px; line-height: 1.65; }

.list { display: flex; flex-direction: column; gap: 10px; }
.card { position: relative; padding: 0; }
.card.unread { border-color: var(--line2); background: var(--surf); }
.card.low { opacity: .82; }

/* 카드 안쪽 전체가 하나의 누름 영역 */
.hit {
  display: block; width: 100%; text-align: left;
  padding: 14px 44px 14px 16px;   /* 오른쪽은 X 자리를 비워 둔다 */
  border-radius: inherit;
  transition: background .12s;
}
.hit:not(:disabled):hover { background: var(--surf2); }
.hit:disabled { cursor: default; }

/* 닫기 — 카드 오른쪽 위 */
.x {
  position: absolute; top: 10px; right: 10px;
  width: 28px; height: 28px; border-radius: 50%;
  display: inline-flex; align-items: center; justify-content: center;
  color: var(--tx3);
  transition: background .15s, color .15s;
}
.x:hover { background: var(--surf2); color: var(--tx); }

.top { display: flex; align-items: center; gap: 7px; flex-wrap: wrap; }
/* 상태 점은 한 가지 색만 쓴다 — 안 읽음이면 주색, 읽었으면 흐린 경계색.
   중요도별로 색을 나누면 목록이 알록달록해지고 "안 읽음"이 안 보인다. */
.dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.dot.new { background: var(--ac); }
.dot.read { background: var(--line2); }
.cname { font-size: 15px; font-weight: 800; letter-spacing: -.03em; }
.when { margin-left: auto; padding-right: 26px; }

.line { font-size: 12.5px; color: var(--tx2); margin: 9px 0 11px; }
.line b { font-weight: 800; color: var(--tx); }

.cmp {
  display: flex; align-items: center; gap: 12px;
  background: var(--surf2); border-radius: 13px; padding: 12px 14px;
}
.hit:not(:disabled):hover .cmp { background: var(--surf3); }
.side { flex: 1; min-width: 0; }
.lbl { font-size: 11px; color: var(--tx3); font-weight: 700; margin-bottom: 6px; }
.side .note { margin-top: 5px; }
.warn { color: var(--busy) !important; font-weight: 700; }
.arw { color: var(--tx3); font-size: 15px; flex-shrink: 0; }

.stamp { margin-top: 8px; }

@media (max-width: 560px) {
  .cmp { flex-direction: column; align-items: stretch; gap: 8px; }
  .arw { transform: rotate(90deg); text-align: center; }
}
</style>
