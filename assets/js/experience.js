/* Time-aware copy and a real, bounded handoff into the personalized Home feed. */
(() => {
  'use strict';
  function greeting(date = new Date(), tone) {
    const hour = date.getHours();
    tone ||= MousikeData.read(MousikeData.keys.ui, {}).greetingTone || 'warm';
    const periods = [
      [5, 'Night owl', 'A little company for the quiet hours.'],
      [8, 'Early bird', 'A fresh start, at your own pace.'],
      [12, 'Good morning', 'Find the sound of your morning.'],
      [15, 'Afternoon reset', 'Take a moment. Find your rhythm.'],
      [18, 'Good afternoon', 'Keep the day moving your way.'],
      [21, 'Good evening', 'Make a little room to unwind.'],
      [24, 'Night owl', 'Settle in. Let the music take its time.']
    ];
    const [, title, subtitle] = periods.find(([end]) => hour < end);
    const minimal = hour < 5 ? 'Good night' : hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening';
    return {title: tone === 'minimal' ? minimal : title, subtitle, hour};
  }
  let greetingKey = '';
  function refreshGreeting() {
    if (document.hidden) return;
    const value = greeting();
    const key = value.title + value.subtitle;
    if (greetingKey && key !== greetingKey && state.page === 'home') render();
    greetingKey = key;
  }
  setInterval(refreshGreeting, 60000);
  document.addEventListener('visibilitychange', refreshGreeting);
  refreshGreeting();

  async function arrival() {
    if (!sessionStorage.getItem('mousike-arrival')) return;
    sessionStorage.removeItem('mousike-arrival');
    if (MousikeData.read(MousikeData.keys.ui, {}).welcomeAnimation === false) return;
    const reduced = document.documentElement.dataset.mousikeMotion === 'reduced';
    const profile = MousikeData.read('aurora_taste_profile', {});
    const overlay = document.createElement('section');
    overlay.className = 'arrival';
    overlay.setAttribute('aria-label', 'Preparing your music space');
    overlay.innerHTML = '<div class="arrival-inner"><img src="/assets/brand/mousike-mark-' + (reduced ? 'static' : 'waveform') + '.svg?v=6" alt="" width="80" height="80"><div class="arrival-kicker">Sound In motion</div><div class="arrival-copy" role="status" aria-live="polite"><h2>Reading your preferences</h2><p></p></div><div class="arrival-track" aria-hidden="true"><span class="on"></span><span></span><span></span><span></span></div><button class="arrival-skip" type="button">Go to my music</button></div>';
    document.body.append(overlay);
    const title = overlay.querySelector('h2'), subtitle = overlay.querySelector('p');
    const steps = overlay.querySelectorAll('.arrival-track span');
    const languages = (profile.languages || []).slice(0, 2).join(' & ');
    subtitle.textContent = (profile.artists?.length ? profile.artists.length + ' favorite artists' : 'Your favorite styles') + (languages ? ' · ' + languages : '');
    let finished = false;
    const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
    function finish() {
      if (finished) return;
      finished = true;
      overlay.classList.add('leaving');
      document.removeEventListener('keydown', escape);
      setTimeout(() => overlay.remove(), reduced ? 0 : 420);
    }
    function escape(event) { if (event.key === 'Escape') finish(); }
    document.addEventListener('keydown', escape);
    overlay.querySelector('button').onclick = finish;
    async function stage(index, heading, copy) {
      if (finished) return;
      const copyElement = overlay.querySelector('.arrival-copy');
      if (!reduced) {
        copyElement.classList.add('changing');
        await wait(110);
        if (finished) return;
      }
      title.textContent = heading;
      subtitle.textContent = copy;
      steps.forEach((step, i) => step.classList.toggle('on', i <= index));
      copyElement.classList.remove('changing');
    }
    const feedReady = window.AuroraSmart?.refresh(false)?.catch(() => null);
    await wait(reduced ? 150 : 550);
    await stage(1, window.AuroraSmart?.feed()?.tracks?.length ? 'Bringing your mix together' : 'Building your mix', 'Your artists, new voices, and room to explore.');
    await wait(reduced ? 150 : 650);
    await stage(2, 'Customising for you', 'Your colors. Your layout. Your listening space.');
    await Promise.all([wait(reduced ? 150 : 550), Promise.race([Promise.resolve(feedReady), wait(4500)])]);
    const ready = window.AuroraSmart?.feed()?.tracks?.length > 0;
    await stage(3, 'Here we go', ready ? 'Your music is ready when you are.' : 'Your space is ready. Music will appear as sources connect.');
    await wait(reduced ? 150 : 650);
    finish();
  }
  window.MousikeExperience = {greeting, arrival};
})();
