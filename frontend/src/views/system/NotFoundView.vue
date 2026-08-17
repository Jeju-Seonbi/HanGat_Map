<template>
  <main class="doc">
    <div class="doc-narrow">
      <div class="fl pane">
        <div class="mark" aria-hidden="true">?</div>
        <h1>찾는 화면이 없어요</h1>
        <p class="lead">주소가 바뀌었거나 지워진 화면일 수 있어요.</p>
        <div class="acts">
          <RouterLink class="btn2 primary" to="/mypage">마이페이지로</RouterLink>
          <RouterLink class="btn2" to="/login">로그인 화면으로</RouterLink>
        </div>
      </div>
    </div>
  </main>
</template>

<style scoped>
.pane { padding: 40px 24px 30px; text-align: center; }
.mark {
  width: 48px; height: 48px; margin: 0 auto 12px; border-radius: 50%;
  background: var(--surf2); color: var(--tx3);
  font-size: 21px; font-weight: 800; display: flex; align-items: center; justify-content: center;
}
h1 { font-size: 19px; font-weight: 800; letter-spacing: -.035em; margin-bottom: 6px; }
.lead { font-size: 12.5px; color: var(--tx2); line-height: 1.65; }
.acts { display: flex; gap: 8px; margin-top: 18px; }
.acts > * { flex: 1; text-align: center; }
</style>
