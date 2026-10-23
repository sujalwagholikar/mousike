const {chromium}=require('playwright');
const assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.MOUSIKE_BROWSER_PATH || undefined,args:['--no-sandbox','--disable-setuid-sandbox','--disable-dev-shm-usage','--use-gl=angle','--use-angle=swiftshader']});
 const page=await browser.newPage();
 await page.addInitScript(()=>{localStorage.setItem('aurora_onboarded','1');localStorage.setItem('aurora_taste_profile',JSON.stringify({name:'Listener',artists:['Lana Del Rey','Arijit Singh','Coldplay'],languages:['Hindi','English'],genres:['Indie'],moods:['Chill']}));});
 await page.goto('http://127.0.0.1:8765/');
 await page.locator('.music-art > img').first().waitFor({timeout:20000});

 await page.setViewportSize({width:1440,height:1000});

 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.evaluate(()=>AuroraPremium.go('library'));
 assert.equal(await page.locator('.library-radio').count(),1);
 assert.equal(await page.locator('.library-radio h2').innerText(),'Your Radio');
 assert(!(await page.locator('#content').innerText()).includes('< div'));
 const mark=await page.locator('.brand>img').getAttribute('src');assert(mark.includes('mark-static'));
 await page.evaluate(()=>openHybrid('AlbumDemo01'));
 await page.locator('.song-line').first().waitFor();
 const expected=await page.evaluate(()=>AuroraPremium.sourceList('album')[0].title);
 const like=page.locator('.song-like').first();
 await like.click();
 assert.equal(await page.locator('.song-like').first().getAttribute('aria-pressed'),'true');
 assert.equal(await page.evaluate(()=>state.current),null);
 let release;const gate=new Promise(resolve=>release=resolve);
 await page.route('**/api/stream/**',async route=>{await gate;await route.continue()});
 await page.locator('.song-primary').first().click();
 assert.equal(await page.locator('#nowtitle').innerText(),expected);
 assert.equal(await page.locator('.premium-player').getAttribute('aria-busy'),'true');
 assert.equal(await page.evaluate(()=>state.current),null);
 assert.equal(await page.locator('#playerLoadStatus').innerText(),'Preparing audio…');
 await page.locator('#toggle').click();
 assert.equal(await page.evaluate(()=>MousikePlayback.status.pending),false);
 release();await page.waitForTimeout(150);
 assert.equal(await page.evaluate(()=>state.current),null);
 await page.locator('.song-primary').first().click();
 await page.waitForFunction(()=>state.current && !audio.paused && !MousikePlayback.status.pending);
 assert.equal(await page.locator('#nowtitle').innerText(),expected);
 assert.equal(await page.locator('.premium-player').getAttribute('aria-busy'),'false');
 const layouts=[];
 for(const width of [1440,1024,768,390,360]){
  await page.setViewportSize({width,height:1000});
  const result=await page.locator('.song-line').first().evaluate(row=>{
   const like=row.querySelector('.song-like').getBoundingClientRect(),time=row.querySelector('.song-duration').getBoundingClientRect(),frame=row.getBoundingClientRect();
   return {width:innerWidth,likeBeforeTime:like.right<=time.left+1,inside:time.right<=frame.right+1,fit:getComputedStyle(row.querySelector('.song-thumb')).objectFit,overflow:document.documentElement.scrollWidth>innerWidth};
  });
  assert(result.likeBeforeTime);assert(result.inside);assert.equal(result.fit,'contain');assert.equal(result.overflow,false);layouts.push(result);
 }
 assert.deepEqual(errors,[]);
 console.log(JSON.stringify({passed:true,expected,layouts,checks:'Real library markup; cropped enlarged mark; album likes do not trigger playback; instant pending metadata before streaming; cancel does not commit; audio starts and loading clears; responsive row frames'}));
 await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
