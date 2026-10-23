/* One shared Web Audio graph. Both playback decks pass through the same effects. */
'use strict';
const studioDefaults = {
  enabled: true,
  preset: 'Flat',
  bands: [0, 0, 0, 0, 0, 0, 0, 0],
  bass: 0,
  vocals: 0,
  treble: 0,
  pan: 0,
  space: 0,
  compress: false,
  visualizer: true
};
let studio = {
  ...studioDefaults,
  ...load('studio-v1', {})
};
if (!Array.isArray(studio.bands) || studio.bands.length !== 8) studio.bands = [...studioDefaults.bands];
let studioContext = null,
  studioNodes = null;
const deckNodes = new WeakMap();

function initStudio() {
  if (studioNodes) return true;
  try {
    const C = window.AudioContext || window.webkitAudioContext;
    if (!C) return false;
    studioContext = new C();
    const input = studioContext.createGain();
    const filters = [60, 170, 350, 700, 1400, 3000, 6000, 12000].map((f, i) => {
      const node = studioContext.createBiquadFilter();
      node.type = i === 0 ? 'lowshelf' : i === 7 ? 'highshelf' : 'peaking';
      node.frequency.value = f;
      node.Q.value = 1.1;
      return node;
    });
    const bass = studioContext.createBiquadFilter();
    bass.type = 'lowshelf';
    bass.frequency.value = 130;
    const treble = studioContext.createBiquadFilter();
    treble.type = 'highshelf';
    treble.frequency.value = 5200;
    const vocal = studioContext.createBiquadFilter();
    vocal.type = 'peaking';
    vocal.frequency.value = 2200;
    vocal.Q.value = .7;
    const pan = studioContext.createStereoPanner(),
      dry = studioContext.createGain(),
      wet = studioContext.createGain(),
      delay = studioContext.createDelay(.45);
    delay.delayTime.value = .11;
    const compressor = studioContext.createDynamicsCompressor(),
      analyser = studioContext.createAnalyser();
    analyser.fftSize = 256;
    let last = input;
    for (const node of [...filters, bass, treble, vocal, pan]) {
      last.connect(node);
      last = node;
    }
    last.connect(dry);
    dry.connect(compressor);
    last.connect(delay);
    delay.connect(wet);
    wet.connect(compressor);
    compressor.connect(analyser);
    analyser.connect(studioContext.destination);
    studioNodes = {
      input,
      filters,
      bass,
      treble,
      vocal,
      pan,
      dry,
      wet,
      compressor,
      analyser
    };
    document.querySelectorAll('audio').forEach(connectDeck);
    applyStudio();
    return true;
  } catch {
    return false;
  }
}

function connectDeck(el) {
  if (!studioContext || deckNodes.has(el)) return deckNodes.get(el);
  const source = studioContext.createMediaElementSource(el),
    gain = studioContext.createGain(),
    normalize = studioContext.createGain(),
    meter = studioContext.createAnalyser();
  meter.fftSize = 2048;
  source.connect(meter);
  source.connect(normalize);
  normalize.connect(gain);
  gain.connect(studioNodes.input);
  const info = {
    source,
    gain,
    normalize,
    meter,
    samples: new Float32Array(2048),
    rms: 0,
    measured: 0,
    replaygain: false
  };
  deckNodes.set(el, info);
  return info;
}

function applyStudio() {
  if (!studioNodes) return;
  const n = studioNodes,
    on = studio.enabled !== false,
    at = studioContext.currentTime;
  const bounded = x => Math.max(-12, Math.min(12, Number(x) || 0));
  n.filters.forEach((f, i) => f.gain.setTargetAtTime(on ? bounded(studio.bands[i]) : 0, at, .04));
  n.bass.gain.setTargetAtTime(on ? bounded(studio.bass) : 0, at, .04);
  n.vocal.gain.setTargetAtTime(on ? bounded(studio.vocals) : 0, at, .04);
  n.treble.gain.setTargetAtTime(on ? bounded(studio.treble) : 0, at, .04);
  n.pan.pan.setTargetAtTime(on ? Math.max(-1, Math.min(1, (Number(studio.pan) || 0) / 100)) : 0, at, .04);
  n.wet.gain.setTargetAtTime(on ? Math.max(0, Math.min(1, Number(studio.space) / 100)) * .22 : 0, at, .04);
  // Headroom anticipates cumulative positive EQ boosts.
  const boost = on ? Math.max(0, ...studio.bands.map(bounded), bounded(studio.bass), bounded(studio.vocals), bounded(studio.treble)) : 0;
  n.input.gain.setTargetAtTime(10 ** (-boost / 24), at, .08);
  n.compressor.threshold.value = on && studio.compress ? -22 : prefs.normalization ? -1 : 0;
  n.compressor.ratio.value = on && studio.compress ? 2.6 : prefs.normalization ? 12 : 1;
  n.compressor.knee.value = on && studio.compress ? 20 : 0;
  n.compressor.attack.value = .003;
}

function resumeStudio() {
  if (initStudio()) studioContext.resume().catch(() => {});
}

function setDeckGain(el, value, seconds = 0) {
  const n = deckNodes.get(el);
  if (!n || !studioContext) return;
  const at = studioContext.currentTime;
  n.gain.gain.cancelScheduledValues(at);
  n.gain.gain.setValueAtTime(n.gain.gain.value, at);
  n.gain.gain.linearRampToValueAtTime(Math.max(0, Math.min(1, value)), at + seconds);
}

function normalizeDeck(el, track) {
  const n = deckNodes.get(el);
  if (!n || !studioContext) return;
  // Prefer supplied ReplayGain; otherwise a slow RMS estimator levels the stream.
  const db = track?.replaygain_db === undefined ? NaN : Number(track.replaygain_db);
  n.replaygain = Number.isFinite(db);
  n.rms = 0;
  n.measured = 0;
  const target = prefs.normalization && Number.isFinite(db) ? 10 ** (Math.max(-12, Math.min(6, db)) / 20) : 1;
  n.normalize.gain.setTargetAtTime(target, studioContext.currentTime, .1);
}

function levelDeck(el, delta) {
  const n = deckNodes.get(el);
  if (!n || !studioContext || !prefs.normalization || n.replaygain || el.paused) return;
  n.meter.getFloatTimeDomainData(n.samples);
  let energy = 0;
  for (const x of n.samples) energy += x * x;
  const rms = Math.sqrt(energy / n.samples.length);
  if (rms < .008) return;
  n.rms = n.rms ? n.rms * .94 + rms * .06 : rms;
  n.measured += delta;
  if (n.measured < 2) return;
  const target = Math.max(.25, Math.min(1.58, .126 / Math.max(.01, n.rms)));
  n.normalize.gain.setTargetAtTime(target, studioContext.currentTime, target < n.normalize.gain.value ? 1.5 : 5);
}
window.MousikeAudio = {
  resume: resumeStudio,
  gain: setDeckGain,
  normalize: normalizeDeck,
  level: levelDeck,
  get context() {
    return studioContext;
  },
  get analyser() {
    return studioNodes?.analyser;
  }
};
