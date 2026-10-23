/* Integration checks against tests/fixture_server.py. No live providers required. */
const {
  chromium
} = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
(async () => {
  const results = [];
  const errors = [];
  const browser = await chromium.launch({
    headless: true,
    executablePath: process.env.MOUSIKE_BROWSER_PATH || undefined,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage', '--autoplay-policy=no-user-gesture-required', '--use-gl=angle', '--use-angle=swiftshader']
  });
  const context = await browser.newContext({
    viewport: {
      width: 1440,
      height: 1000
    }
  });
  const page = await context.newPage();
  page.on('pageerror', e => errors.push(e.stack));
  page.on('console', m => {
    if (m.type() === 'error' && !m.text().includes('favicon')) errors.push(m.text());
  });
  await context.addInitScript(() => {
    if (localStorage.getItem('browser-test-seeded')) return;
    localStorage.setItem('browser-test-seeded', '1');
    localStorage.setItem('aurora_onboarded', '1');
    localStorage.setItem('aurora_taste_profile', JSON.stringify({
      name: 'Listener',
      artists: ['Lana Del Rey', 'Arijit Singh', 'The Weeknd', 'Billie Eilish', 'Coldplay', 'Shreya Ghoshal'],
      languages: ['Hindi', 'English'],
      genres: ['Indie', 'Bollywood'],
      moods: ['Chill', 'Dreamy'],
      discovery: 45
    }));
    localStorage.setItem('aurora-intelligence-v2-settings', JSON.stringify({
      autoFeed: true,
      activity: true,
      radio: true,
      feedInterval: 30
    }));
  });
  async function check(name, fn) {
    try {
      await fn();
      results.push({
        name,
        passed: true
      });
      console.log('PASS ' + name);
    } catch (e) {
      results.push({
        name,
        passed: false,
        error: String(e.stack || e)
      });
      console.log('FAIL ' + name + ' ' + e.message);
      throw e;
    }
  }
  const url = 'http://127.0.0.1:8765';
  const out = process.env.MOUSIKE_TEST_OUTPUT || path.join(__dirname, '..', 'test-results');
  fs.mkdirSync(out, {
    recursive: true
  });
  try {
    await page.goto(url);
    await page.waitForFunction(() => window.AuroraSmart?.tracks().length > 20);
    await check('Home renders real backend sections and artist diversity', async () => {
      const value = await page.evaluate(() => ({
        tracks: AuroraSmart.tracks().length,
        artists: new Set(AuroraSmart.tracks().slice(0, 12).map(t => t.artist)).size,
        sections: AuroraSmart.feed().sections.map(s => s.id)
      }));
      assert(value.tracks >= 40);
      assert(value.artists >= 6);
      assert(value.sections.includes('weekly'));
      assert(value.sections.includes('mood'));
      await page.screenshot({
        path: path.join(out, 'home-desktop.png')
      });
    });
    await check('Playback starts through the actual FastAPI streaming proxy', async () => {
      await page.locator('.hero-play').click();
      await page.waitForFunction(() => window.MousikePlayback?.media.currentTime > .25 && !MousikePlayback.media.paused);
      assert(await page.evaluate(() => state.current.id.startsWith('Test')));
    });
    await check('Navigation preserves the same media session', async () => {
      const before = await page.evaluate(() => ({
        src: audio.currentSrc,
        time: audio.currentTime
      }));
      await page.locator('.rail [data-page="library"]').click();
      await page.waitForTimeout(250);
      const after = await page.evaluate(() => ({
        src: audio.currentSrc,
        time: audio.currentTime
      }));
      assert.equal(after.src, before.src);
      assert(after.time > before.time);
    });
    await check('A single Space press toggles once', async () => {
      await page.locator('#content').focus();
      await page.keyboard.press('Space');
      assert(await page.evaluate(() => audio.paused));
      await page.keyboard.press('Space');
      await page.waitForTimeout(150);
      assert(!(await page.evaluate(() => audio.paused)));
    });
    await check('Source recovery resumes the same recording at its prior position', async () => {
      const before = await page.evaluate(() => ({
        id: state.current.id,
        position: audio.currentTime
      }));
      await page.evaluate(() => audio.dispatchEvent(new Event('error')));
      await page.waitForFunction(() => audio.currentSrc.includes('retry=1') && !audio.paused && audio.currentTime > .1);
      assert.equal(await page.evaluate(() => state.current.id), before.id);
      assert((await page.evaluate(() => audio.currentTime)) >= before.position - .15);
    });
    await check('Full-screen player synchronizes progress and queue controls', async () => {
      await page.locator('#openExpanded').click();
      const frame = page.frameLocator('#mousikePlayerLayer iframe');
      await frame.locator('#song').waitFor();
      await page.waitForTimeout(350);
      assert.equal(await frame.locator('#song').innerText(), await page.evaluate(() => state.current.title));
      const initial = await page.evaluate(() => state.queue.length);
      await frame.locator('[data-remove]').first().click();
      await page.waitForTimeout(300);
      assert.equal(await page.evaluate(() => state.queue.length), initial - 1);
      await frame.locator('#like').click();
      await page.waitForTimeout(150);
      assert(await page.evaluate(() => state.likes.length > 0));
      await page.screenshot({
        path: path.join(out, 'player-desktop.png')
      });
      await frame.locator('#close').click();
    });
    await check('Audio Studio filters and live spectrum are connected to Web Audio', async () => {
      await page.evaluate(() => {
        studio.enabled = true;
        studio.visualizer = true;
        studio.bands[4] = 4;
        applyStudio();
      });
      await page.waitForTimeout(300);
      assert((await page.evaluate(() => studioNodes.filters[4].gain.value)) > 3.8);
      await page.locator('#openExpanded').click();
      const frame = page.frameLocator('#mousikePlayerLayer iframe');
      await frame.locator('#song').waitFor();
      await page.waitForTimeout(700);
      assert(await frame.locator('#spectrum').isVisible());
      await frame.locator('#close').click();
      await page.evaluate(() => {
        studio.bands[4] = 0;
        applyStudio();
      });
    });
    await check('Seeking does not fabricate meaningful listening events', async () => {
      const id = await page.evaluate(() => state.current.id);
      const before = await page.evaluate(() => MousikeData.read(MousikeData.keys.events, []).filter(e => ['listen', 'complete'].includes(e.kind)).length);
      await page.evaluate(() => MousikePlayback.seek(.8));
      await page.waitForTimeout(250);
      const after = await page.evaluate(() => MousikeData.read(MousikeData.keys.events, []).filter(e => ['listen', 'complete'].includes(e.kind)).length);
      assert.equal(after, before);
    });
    await check('Settings apply during playback and export includes playlists', async () => {
      await page.locator('#settingsNav').click();
      const f = page.frameLocator('#mousikeSettingsLayer iframe');
      await f.locator('[data-section="playback"]').click();
      await f.locator('#crossfade').selectOption('2');
      await f.locator('#prebuffer').check();
      await page.waitForFunction(() => prefs.crossfade === '2' && prefs.prebuffer === true);
      assert(!(await page.evaluate(() => audio.paused)));
      await f.locator('[data-section="personal"]').click();
      await f.locator('#discoveryLevel').fill('72');
      await f.locator('#discoveryLevel').dispatchEvent('change');
      await page.waitForFunction(() => MousikeData.read('aurora_taste_profile', {}).discovery === 72);
      await f.locator('#closeSettings').click();
      assert(await page.evaluate(() => Array.isArray(MousikeData.exportData().playlists)));
    });
    await check('Appearance, text size and corner preferences apply across views', async () => {
      await page.locator('#settingsNav').click();
      const f = page.frameLocator('#mousikeSettingsLayer iframe');
      await f.locator('[data-theme="light"]').click();
      await f.locator('#fontScale').selectOption('large');
      await f.locator('#rounding').selectOption('soft');
      await page.waitForFunction(() => document.documentElement.dataset.mousikeTheme === 'light' && document.documentElement.dataset.mousikeFont === 'large');
      await f.locator('[data-theme="dark"]').click();
      await f.locator('#fontScale').selectOption('normal');
      await f.locator('#rounding').selectOption('rounded');
      await f.locator('#closeSettings').click();
    });
    await check('Now Playing sidebar opens a working track context menu', async () => {
      await page.locator('.player-panel-toggle').click();
      await page.locator('[data-action="menu-current"]').click();
      assert.equal(await page.locator('.menu-pop').count(), 1);
      await page.keyboard.press('Escape');
      await page.locator('#closeNowPanel').click();
    });
    await check('Playlist creation, context menu and feedback reach persisted state', async () => {
      await page.locator('#railCreate').click();
      await page.locator('#textDialogInput').fill('Integration playlist');
      await page.locator('#textDialog button[value="ok"]').click();
      await page.waitForFunction(() => AuroraPremium.playlists.some(p => p.title === 'Integration playlist'));
      await page.evaluate(() => AuroraPremium.go('home'));
      await page.locator('.music-card .card-menu').first().click();
      await page.locator('[data-menu="add-playlist"]').first().click();
      assert(await page.evaluate(() => AuroraPremium.playlists[0].tracks.length === 1));
      await page.locator('.music-card .card-menu').first().click();
      await page.locator('[data-menu="less-artist"]').click();
      assert(await page.evaluate(() => MousikeData.read(MousikeData.keys.events, []).some(e => e.kind === 'less_artist')));
    });
    await check('Semantic search, real artist results and collection navigation', async () => {
      await page.locator('#query').fill('dreamy late-night indie songs');
      await page.keyboard.press('Enter');
      await page.waitForFunction(() => state.page === 'search' && !state.busy && state.tracks.length > 0);
      assert(await page.evaluate(() => state.searchPlan.concepts.includes('dreamy')));
      await page.locator('[data-action="search-type"][data-type="artists"]').click();
      await page.locator('.search-artist-bubble').first().click();
      await page.waitForFunction(() => state.page === 'artist' && AuroraPremium.artist?.loading === false);
      assert((await page.locator('.artist-hero h1').innerText()).length > 1);
      await page.locator('[data-action="album"]').first().click();
      await page.waitForFunction(() => state.page === 'album' && !state.albumLoading && state.collection?.tracks.length > 0);
    });
    await check('Hybrid chapter playback shares the same media source across cuts', async () => {
      await page.evaluate(() => {
        prefs.autoplay = true;
        prefs.crossfade = 0;
        return openHybrid('AlbumDemo01');
      });
      await page.waitForFunction(() => state.collection?.tracks[0]?.segment === true);
      await page.locator('[data-action="play-all"]').click();
      await page.waitForFunction(() => state.current?.segment && !audio.paused && audio.currentTime > .2);
      const before = await page.evaluate(() => ({
        src: audio.currentSrc,
        id: audio.id
      }));
      await page.evaluate(() => MousikePlayback.seek(.996));
      await page.waitForFunction(() => state.current?.start === 30);
      assert.equal(await page.evaluate(() => audio.currentSrc), before.src);
      assert.equal(await page.evaluate(() => audio.id), before.id);
      assert((await page.evaluate(() => relativeClipTime())) < 2);
    });
    await check('Shuffle visits distinct songs and repeat-one honors manual Next', async () => {
      await page.evaluate(async () => {
        await playAt(0, AuroraSmart.tracks().slice(0, 10));
        state.shuffle = true;
      });
      const ids = [];
      for (let i = 0; i < 4; i++) {
        await page.evaluate(() => next());
        await page.waitForTimeout(100);
        ids.push(await page.evaluate(() => state.current.id));
      }
      assert.equal(new Set(ids).size, ids.length);
      await page.evaluate(() => {
        state.repeat = 2;
      });
      const current = await page.evaluate(() => state.current.id);
      await page.locator('#next').click();
      await page.waitForTimeout(300);
      assert.notEqual(await page.evaluate(() => state.current.id), current);
    });
    await check('Crossfade overlaps two decoded decks and completes cleanly', async () => {
      await page.evaluate(async () => {
        state.shuffle = false;
        state.repeat = 0;
        prefs.crossfade = 2;
        prefs.prebuffer = true;
        await playAt(0, AuroraSmart.tracks().slice(0, 6));
      });
      await page.waitForFunction(() => MousikePlayback.status.prepared);
      await page.waitForTimeout(450);
      const old = await page.evaluate(() => state.current.id);
      await page.evaluate(() => MousikePlayback.seek(.86));
      await page.waitForFunction(id => state.current.id !== id, old);
      assert(await page.evaluate(() => decks.filter(d => !d.paused).length === 2));
      await page.waitForTimeout(2100);
      assert.equal(await page.evaluate(() => decks.filter(d => !d.paused).length), 1);
    });
    await check('Imported synchronized lyrics render and remain local', async () => {
      await page.locator('#openExpanded').click();
      const frame = page.frameLocator('#mousikePlayerLayer iframe');
      await frame.locator('#lyricsTab').click();
      await frame.locator('#lyricsFile').setInputFiles({
        name: 'permitted-test.lrc',
        mimeType: 'text/plain',
        buffer: Buffer.from('[00:00.00]Test lyric line\n[00:02.00]Another test line')
      });
      assert.equal(await frame.locator('#lyricsLines button').count(), 2);
      assert(await page.evaluate(() => Object.keys(MousikeData.read('mousike-lyrics-v3', {})).length === 1));
      await frame.locator('#close').click();
    });
    await check('Desktop, tablet and mobile surfaces have no horizontal overflow', async () => {
      for (const width of [1440, 1024, 768, 390, 360]) {
        await page.setViewportSize({
          width,
          height: 844
        });
        await page.evaluate(() => AuroraPremium.go('home'));
        await page.waitForTimeout(100);
        const overflow = await page.evaluate(() => document.documentElement.scrollWidth > innerWidth || document.getElementById('main').scrollWidth > document.getElementById('main').clientWidth + 1);
        assert(!overflow, 'Overflow at ' + width);
      }
      await page.setViewportSize({
        width: 390,
        height: 844
      });
      await page.screenshot({
        path: path.join(out, 'home-mobile.png')
      });
      await page.locator('#cover').click();
      const frame = page.frameLocator('#mousikePlayerLayer iframe');
      await frame.locator('#song').waitFor();
      await page.waitForTimeout(200);
      assert(!(await frame.locator('html').evaluate(el => el.scrollWidth > innerWidth)));
      await page.screenshot({
        path: path.join(out, 'player-mobile.png')
      });
      await frame.locator('#close').click();
    });
    await check('Onboarding saves data and transitions on Home', async () => {
      await page.goto(url + '/onboarding.html?edit=1');
      await page.locator('#name').fill('Test listener');
      await page.locator('#continue').click();
      await page.locator('#continue').click();
      await page.locator('#continue').click();
      await page.locator('#continue').click();
      await page.waitForURL(url + '/');
      await page.locator('.arrival').waitFor();
      assert(/Taking a look|Building your mix|Tuning your space|Here we go/.test(await page.locator('.arrival').innerText()));
      await page.waitForTimeout(2900);
      assert.equal(await page.locator('.arrival').count(), 0);
    });
    await check('All browser operations complete without page errors or CSP violations', async () => assert.deepEqual(errors, []));
  } finally {
    fs.writeFileSync(path.join(out, 'browser-results.json'), JSON.stringify({
      results,
      errors
    }, null, 2));
    await browser.close();
  }
  console.log(JSON.stringify({
    passed: results.filter(r => r.passed).length,
    total: results.length,
    errors: errors.length
  }));
})().catch(e => {
  console.error(e);
  process.exit(1);
});
