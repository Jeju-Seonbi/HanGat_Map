// Local sample data only; no production API or data changes.
const {chromium}=require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert=require('node:assert/strict');
const {join}=require('node:path');
const {tmpdir}=require('node:os');
const base=process.env.SAVED_COURSES_TEST_URL || 'http://127.0.0.1:5210';
(async()=>{
 const browser=await chromium.launch({headless:true});
 try {
  for(const mobile of [false,true]) {
   const context=await browser.newContext({viewport:mobile?{width:390,height:844}:{width:1440,height:1000},isMobile:mobile,hasTouch:mobile});
   const page=await context.newPage();
   await page.goto(base+'/tests/browser/saved-courses.html?budget=1');
   const cards=page.locator('.library-list .course-tab');
   await cards.first().waitFor();
   assert.match(await cards.nth(0).innerText(),/전체 예산 100,000원/);
   assert.match(await cards.nth(1).innerText(),/전체 예산 200,000원/);
   await cards.first().click();
   await page.locator('.saved-budget summary').click();
   const panel=page.locator('.saved-budget');
   assert.match(await panel.innerText(),/100,000원/);
   assert.match(await panel.innerText(),/16,000 ~ 24,000원/);
   assert.match(await panel.innerText(),/76,000원/);
   assert.match(await panel.innerText(),/가격 미상/);
   await panel.scrollIntoViewIfNeeded();
   const box=await panel.boundingBox();
   assert.ok(box && box.x>=0 && box.x+box.width<=(mobile?391:1441));
   const path=join(tmpdir(),`hangat-budget-${mobile?'mobile':'desktop'}.png`);
   await page.screenshot({path});
   console.log(`${mobile?'mobile':'desktop'} budget PASS ${path}`);
   await context.close();
  }
 } finally {await browser.close()}
})().catch(e=>{console.error(e);process.exit(1)});
