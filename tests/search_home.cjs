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
 await page.evaluate(()=>{
  MousikeData.write(MousikeData.keys.history,[{id:'HistOnly001',title:'Previously played only',artist:'History Artist',duration:180}]);
  MousikeData.write(MousikeData.keys.likes,[{id:'LikedOnly01',title:'Saved favorite',artist:'Liked Artist',duration:190}]);
  MousikeData.write(MousikeData.keys.playlists,[{id:'p1',title:'My music',tracks:[{id:'SavedOnly01',title:'Saved playlist track',artist:'Playlist Artist',duration:200}]}]);
  MousikeData.write(MousikeData.keys.following,['Followed Artist']);
 });
 await page.reload();await page.locator('.card-main').first().waitFor();
 await page.locator('.editorial-shelf h2').first().scrollIntoViewIfNeeded();
 assert.equal((await page.locator('.editorial-shelf h2').first().textContent()).trim(),'Made for You');
 assert.equal(await page.locator('.editorial-shelf h2').filter({hasText:'Made for You'}).count(),1);
 assert(!(await page.locator('.editorial-shelf').first().innerText()).includes('Previously played only'));
 const payload=await page.evaluate(()=>AuroraSmart.payload());
 assert(payload.activity.some(e=>e.kind==='like'&&e.track.title==='Saved favorite'));
 assert(payload.activity.some(e=>e.kind==='playlist_add'&&e.track.title==='Saved playlist track'));
 assert(payload.activity.some(e=>e.kind==='follow'&&e.track.artist==='Followed Artist'));
 assert(payload.activity.length<=700);assert.equal(payload.api_key,'');
 const keyed=await page.evaluate(()=>{sessionStorage.setItem('mousike-gemini-key','test-only-not-a-real-key');const p=AuroraSmart.payload();sessionStorage.removeItem('mousike-gemini-key');return {key:p.api_key,signals:p.activity.map(e=>e.kind+'|'+e.track.title)}});
 assert.equal(keyed.key,'test-only-not-a-real-key');assert.deepEqual(keyed.signals,payload.activity.map(e=>e.kind+'|'+e.track.title));
 await page.locator('.artist-gallery').scrollIntoViewIfNeeded();
 const portraitWidth=await page.locator('.artist-gallery button>span').first().evaluate(el=>el.getBoundingClientRect().width);assert(portraitWidth>90);
 await page.evaluate(()=>search('indie songs'));
 await page.locator('.search-featured > img').waitFor();
 const layouts=[];
 for(const width of [1440,1024,820,768,390,360]){
  await page.setViewportSize({width,height:1000});
  const result=await page.evaluate(()=>{
   const row=document.querySelector('.search-mini-list .song-line'),more=row.querySelector('.song-more').getBoundingClientRect(),primary=row.querySelector('.song-primary').getBoundingClientRect(),bounds=row.getBoundingClientRect();
   const img=document.querySelector('.search-featured>img'),art=img.getBoundingClientRect(),card=img.parentElement.getBoundingClientRect();
   return {columns:getComputedStyle(row).gridTemplateColumns,more:[more.x,more.y,more.width,more.height],primary:[primary.x,primary.y,primary.width,primary.height],row:[bounds.x,bounds.y,bounds.width,bounds.height],width:innerWidth,fit:getComputedStyle(img).objectFit,thumbFit:getComputedStyle(row.querySelector('.song-thumb')).objectFit,moreInRow:more.right<=bounds.right+1&&more.top<primary.bottom,artInFrame:art.left>=card.left&&art.right<=card.right,overflow:document.documentElement.scrollWidth>innerWidth};
  });
  assert.equal(result.fit,'contain');assert.equal(result.thumbFit,'contain');assert(result.moreInRow);assert(result.artInFrame);assert.equal(result.overflow,false);layouts.push(result);
 }
 assert.deepEqual(errors,[]);
 console.log(JSON.stringify({passed:true,portraitWidth,signalKinds:[...new Set(payload.activity.map(e=>e.kind))],layouts,checks:'Made for You first and once, separate from history; larger artist gallery; shared activity with and without key; search artwork and row controls fit'}));
 await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
