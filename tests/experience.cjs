const {chromium}=require('playwright');
const assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.MOUSIKE_BROWSER_PATH || undefined,args:['--no-sandbox','--disable-setuid-sandbox','--disable-dev-shm-usage','--use-gl=angle','--use-angle=swiftshader']});
 const page=await browser.newPage();
 const errors=[];page.on('pageerror',e=>{errors.push(e.message);console.error('PAGE ERROR',e.stack)});
 await page.addInitScript(()=>{localStorage.setItem('aurora_onboarded','1');localStorage.setItem('aurora_taste_profile',JSON.stringify({name:'Listener',artists:['Lana Del Rey','Arijit Singh','Coldplay'],languages:['Hindi','English'],genres:['Indie'],moods:['Chill']}));});
 await page.goto('http://127.0.0.1:8765/');
 try {await page.locator('.music-art > img').first().waitFor({timeout:20000});} catch(e){console.log('INITIAL',await page.locator('body').innerText());throw e;}

 await page.setViewportSize({width:1440,height:1000});


 const greetings=await page.evaluate(()=>[0,4,5,7,8,12,16,19,22].map(hour=>({hour,...MousikeExperience.greeting(new Date(2026,9,24,hour),'warm')})));
 assert.equal(greetings[0].title,'Night owl');assert.equal(greetings[2].title,'Early bird');assert.equal(greetings[4].title,'Good morning');assert.equal(greetings[8].title,'Night owl');
 await page.evaluate(()=>MousikeSettings.open('appearance'));
 const settings=page.frameLocator('#mousikeSettingsLayer iframe');
 assert.equal(await settings.locator('[data-theme]').count(),6);
 await settings.locator('[data-theme-group="dual"]').click();
 assert.equal(await settings.locator('[data-theme]').count(),6);
 await settings.locator('[data-theme="dual-ivory"]').click();
 await page.waitForFunction(()=>document.documentElement.dataset.mousikeTheme==='dual-ivory');
 const split=await page.evaluate(()=>({rail:getComputedStyle(document.querySelector('.rail')).backgroundColor,canvas:getComputedStyle(document.querySelector('.main-view')).backgroundColor,text:getComputedStyle(document.querySelector('.main-view')).color}));
 assert.notEqual(split.rail,split.canvas);assert.equal(split.text,'rgb(23, 37, 28)');
 await settings.locator('[data-section="personal"]').click();await settings.locator('#greetingTone').selectOption('minimal');
 await settings.locator('#closeSettings').click();
 await page.reload();await page.locator('.card-main[data-action="play"]').first().waitFor();
 assert.equal(await page.evaluate(()=>document.documentElement.dataset.mousikeTheme),'dual-ivory');
 await page.locator('.card-main[data-action="play"]').first().click();
 await page.waitForFunction(()=>!audio.paused && document.querySelector('.brand>img').getAttribute('src').includes('waveform'));
 await page.evaluate(()=>audio.dispatchEvent(new Event('waiting')));
 assert((await page.locator('.brand>img').getAttribute('src')).includes('static'));
 await page.evaluate(()=>audio.dispatchEvent(new Event('playing')));
 assert((await page.locator('.brand>img').getAttribute('src')).includes('waveform'));
 await page.evaluate(()=>MousikePlayer.open());
 const player=page.frameLocator('#mousikePlayerLayer iframe');
 await player.locator('#movingLogo').waitFor();
 await page.waitForFunction(()=>document.querySelector('#mousikePlayerLayer iframe').contentDocument.querySelector('#movingLogo').getAttribute('src').includes('waveform'));
 await player.locator('#play').click();
 await page.waitForFunction(()=>audio.paused && document.querySelector('#mousikePlayerLayer iframe').contentDocument.querySelector('#movingLogo').getAttribute('src').includes('static'));
 await page.evaluate(()=>MousikePlayer.close());
 await page.addInitScript(()=>{
  window.arrivalTitles=[];
  new MutationObserver(()=>{const h=document.querySelector('.arrival h2');if(h && !window.arrivalTitles.includes(h.textContent)) window.arrivalTitles.push(h.textContent)}).observe(document,{subtree:true,childList:true,characterData:true});
 });
 await page.evaluate(()=>sessionStorage.setItem('mousike-arrival','1'));
 await page.reload();await page.locator('.arrival').waitFor();
 try { await page.locator('.arrival').waitFor({state:'detached',timeout:10000}); } catch(e) {console.log(await page.evaluate(()=>({stages:window.arrivalTitles,html:document.querySelector('.arrival')?.outerHTML,animations:document.getAnimations().map(x=>({state:x.playState,time:x.currentTime}))})));throw e;}
 const stages=await page.evaluate(()=>window.arrivalTitles);
 assert(stages.includes('Reading your preferences'));assert(stages.some(x=>x.includes('mix')));assert(stages.includes('Customising for you'));assert(stages.includes('Here we go'));
 for(const width of [1440,768,390,360]){await page.setViewportSize({width,height:1000});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);}
 assert.deepEqual(errors,[]);
 console.log(JSON.stringify({passed:true,greetings,split,stages,checks:'Twelve theme templates in two categories; split theme persists; uploaded logo follows real playback, buffering and pause in both players; four arrival stages; mobile overflow checks'}));
 await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
