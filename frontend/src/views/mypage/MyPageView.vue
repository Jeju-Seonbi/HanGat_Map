<script setup>
/**
 * 마이페이지 셸.
 *
 * Stitch 시안 구성:
 *   ① 프로필 히어로 — 큰 둥근 카드(rounded-[2rem]), 배경에 유기적인 형태 두 개,
 *      그라데이션 링을 두른 아바타, 인사말, 지표 두 개
 *   ② 아래 2단 — 왼쪽 세로 알약 탭(아이콘 + 알림 점), 오른쪽 내용
 *   좁은 화면에서는 탭이 가로 스크롤로 눕는다(시안 `flex md:flex-col`).
 *
 * 지표는 **실제 개수**를 API 에서 읽는다. 시안의 5·12 는 목업 숫자라 쓰지 않았다.
 */
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../../stores/auth.js'
import { useUiStore } from '../../stores/ui.js'
import { listAlerts, listSavedCourses, listMyReviews } from '../../api/mypage.js'
import AppIcon from '../../components/common/AppIcon.vue'

const auth = useAuthStore()
const ui = useUiStore()
const route = useRoute()

const unread = ref(0)
const courseCount = ref(null)
const reviewCount = ref(null)

async function loadUnread () {
  try {
    unread.value = (await listAlerts({ onlyUnread: true })).unread
  } catch {
    unread.value = 0
  }
}

async function loadStats () {
  // 개수만 필요하므로 size 를 1 로 두고 total 만 읽는다
  const [c, r] = await Promise.allSettled([
    listSavedCourses({ size: 1 }),
    listMyReviews({ size: 1 })
  ])
  courseCount.value = c.status === 'fulfilled' ? c.value.total : null
  reviewCount.value = r.status === 'fulfilled' ? r.value.total : null
}

onMounted(() => { loadUnread(); loadStats() })
watch(() => [route.fullPath, ui.alertsVersion], () => { loadUnread(); loadStats() })

/*
  ⚠️ 2026-08-15 — '저장한 코스' 탭을 뺐다.
     라우트(`/mypage/courses`, `/mypage/courses/:courseId`)와 화면은 **그대로 남겨 뒀다.**
     주소를 직접 치면 열리고, 되살릴 때 이 배열에 한 줄 넣으면 끝이다.
     다만 화면에서 닿을 길이 없어져 MY_001~MY_003(코스 목록·상세·공유)의
     진입점이 사라진 상태라는 걸 적어 둔다.
*/
const TABS = [
  { name: 'my-reviews', label: '작성한 리뷰', icon: 'album', match: p => p === '/mypage/reviews' },
  { name: 'my-favorites', label: '찜한 장소', icon: 'bookmark', match: p => p === '/mypage/favorites' },
  { name: 'my-alerts', label: '알림 내역', icon: 'inbox', match: p => p === '/mypage/alerts', dot: true },
  { name: 'my-profile', label: '설정', icon: 'tune', match: p => p === '/mypage/profile' }
]
</script>

<template>
  <main class="doc">
    <div class="doc-in">
      <!-- ① 프로필 히어로 -->
      <section class="hero">
        <span class="blob b1" aria-hidden="true" />
        <span class="blob b2" aria-hidden="true" />

        <div class="hero-in">
          <div class="avwrap">
            <span class="avring">
              <span class="av">{{ auth.initial }}</span>
            </span>
          </div>

          <div class="hero-txt">
            <h1>{{ auth.displayName }}님, 이번엔 어디로 떠나볼까요?</h1>
            <p class="sub">제주에서 가장 한갓진 시간대를 모아 뒀어요.</p>

            <div class="stats">
              <span class="tagline">
                <AppIcon name="flower" :size="16" fill />
                조용한 여행자
              </span>

              <span class="stat">
                <em class="lbl">저장한 코스</em>
                <b class="tnum">{{ courseCount ?? '–' }}<i>개</i></b>
              </span>

              <span class="stat">
                <em class="lbl">작성한 리뷰</em>
                <b class="tnum">{{ reviewCount ?? '–' }}<i>개</i></b>
              </span>
            </div>
          </div>
        </div>
      </section>

      <!-- ② 2단: 탭 + 내용 -->
      <div class="cols">
        <aside class="side">
          <nav class="tabs thin" aria-label="마이페이지 메뉴">
            <RouterLink
              v-for="t in TABS"
              :key="t.name"
              :to="{ name: t.name }"
              class="mtab"
              :class="{ on: t.match(route.path) }"
            >
              <AppIcon :name="t.icon" :size="20" />
              <span class="txt">{{ t.label }}</span>
              <span v-if="t.dot && unread" class="dot" :aria-label="`읽지 않은 알림 ${unread}건`" />
            </RouterLink>
          </nav>
        </aside>

        <div class="body">
          <RouterView />
        </div>
      </div>

      <p class="foot note">
        혼잡 · 날씨 · 주소는 <b>샘플 예보</b>라 실제와 다를 수 있어요.
        실서비스는 한국관광공사 · 행정안전부 · 기상청 자료를 씁니다.
      </p>
    </div>
  </main>
</template>

<style scoped>
/* ── ① 히어로 ── */
.hero {
  position: relative;
  background: var(--surf);
  border: 1px solid var(--line);
  border-radius: var(--r-2xl);
  padding: var(--sp-xl);
  margin-bottom: var(--sp-xl);
  overflow: hidden;
  box-shadow: var(--sh-soft);
}

/*
  시안의 `organic-shape` — 반지름을 네 모서리마다 다르게 준 비대칭 원.
  장식이라 aria-hidden 이고, 내용 위로 올라오지 않게 z-index 를 낮춘다.
*/
.blob { position: absolute; display: block; pointer-events: none; }
.b1 {
  top: -80px; right: -60px; width: 280px; height: 280px;
  background: var(--ac-bg);
  border-radius: 62% 38% 46% 54% / 54% 47% 53% 46%;
  opacity: .8;
}
.b2 {
  bottom: -110px; left: -70px; width: 300px; height: 300px;
  background: var(--sky);
  border-radius: 41% 59% 62% 38% / 47% 40% 60% 53%;
  opacity: .55;
}

.hero-in {
  position: relative; z-index: 1;
  display: flex; align-items: flex-start; gap: var(--sp-lg);
}

/* 시안: `p-1 bg-gradient-to-tr from-primary to-primary-container` 링 */
.avring {
  display: block; width: 128px; height: 128px; border-radius: 50%; padding: 4px;
  background: linear-gradient(to top right, var(--ac), var(--ac-soft));
}
.av {
  width: 100%; height: 100%; border-radius: 50%;
  border: 4px solid var(--surf);
  background: var(--ac-bg); color: var(--ac-dk);
  font-family: var(--font-head); font-size: 40px; font-weight: 900;
  display: flex; align-items: center; justify-content: center;
}

.hero-txt { flex: 1; min-width: 0; padding-top: 6px; }
h1 { font-size: 28px; letter-spacing: -.03em; margin-bottom: 8px; }
.sub { font-size: 15px; color: var(--tx2); margin-bottom: var(--sp-md); }

.stats { display: flex; flex-wrap: wrap; align-items: center; gap: var(--sp-md); }
.tagline {
  display: inline-flex; align-items: center; gap: 6px;
  background: var(--ac-bg); color: var(--ac-dk);
  font-family: var(--font-head); font-size: 13px; font-weight: 700;
  padding: 8px 16px; border-radius: var(--r-xl);
}
.stat {
  display: flex; flex-direction: column; gap: 2px;
  padding-left: var(--sp-md); border-left: 1px solid var(--line);
}
.stat .lbl { font-style: normal; font-size: 11px; letter-spacing: .1em; }
.stat b {
  font-family: var(--font-head); font-size: 22px; font-weight: 800; color: var(--ac);
}
.stat b i { font-style: normal; font-size: 13px; font-weight: 400; color: var(--tx2); margin-left: 3px; }

/* ── ② 2단 ── */
.cols { display: flex; gap: var(--gutter); align-items: flex-start; }
.side { width: 240px; flex-shrink: 0; }
.body { flex: 1; min-width: 0; }

.tabs { display: flex; flex-direction: column; gap: 8px; }
.mtab {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 20px; border-radius: var(--r-xl);
  border: 1px solid transparent;
  font-family: var(--font-head); font-size: 14px; font-weight: 700;
  color: var(--tx2);
  transition: background .2s, color .2s, border-color .2s;
}
.mtab:hover { background: var(--surf); color: var(--tx); border-color: var(--line); }
.mtab.on {
  background: var(--surf); color: var(--ac);
  border-color: var(--line); box-shadow: var(--sh);
}
.mtab .txt { flex: 1; min-width: 0; }
/* 시안: 알림 탭 오른쪽 끝의 작은 점 (숫자 대신) */
.dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: var(--mid-st); flex-shrink: 0;
}

.foot { margin-top: var(--sp-xl); padding-top: var(--sp-md); border-top: 1px solid var(--line); }
.foot b { color: var(--tx2); font-weight: 700; }

/* ── 좁은 화면: 시안의 `flex md:flex-col` — 탭이 가로로 눕는다 ── */
@media (max-width: 900px) {
  .hero { padding: var(--sp-lg); border-radius: var(--r-xl); }
  .hero-in { flex-direction: column; align-items: center; text-align: center; }
  .avring { width: 96px; height: 96px; }
  .av { font-size: 30px; }
  .hero-txt { padding-top: 0; }
  h1 { font-size: 22px; }
  .stats { justify-content: center; }

  .cols { flex-direction: column; }
  .side { width: 100%; }
  .tabs { flex-direction: row; overflow-x: auto; padding-bottom: 4px; }
  .mtab { flex-shrink: 0; padding: 12px 16px; }
}
</style>
