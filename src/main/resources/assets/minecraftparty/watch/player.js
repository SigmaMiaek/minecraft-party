'use strict';
// Official YouTube IFrame API only: no extraction, ad filtering, account handling or arbitrary navigation.
let player, desired, ready = false, unlocked = false, receivedAt = 0, lastSeek = 0, appliedRevision = -1, lastStatus = '';
const notice = document.getElementById('notice'), enable = document.getElementById('enable');
function report(value) {
  if (value === lastStatus) return;
  lastStatus = value;
  fetch('status?value=' + encodeURIComponent(value), {cache: 'no-store'}).catch(() => {});
}
function sync() {
  if (!ready || !desired || !unlocked) return;
  const now = performance.now(), state = player.getPlayerState();
  const changed = appliedRevision !== desired.revision;
  const target = desired.position + (desired.playing ? (now - receivedAt) / 1000 : 0);
  player.setVolume(desired.volume);
  if (state === 0 && !changed) return; // An ended video stays ended until the host changes playback.
  if (changed || state !== 3 && Math.abs(player.getCurrentTime() - target) > 1.5 && now - lastSeek > 3000) {
    player.seekTo(target, true); lastSeek = now;
  }
  appliedRevision = desired.revision;
  if (desired.playing) { if (state !== 1 && state !== 3) player.playVideo(); }
  else if (state !== 2) player.pauseVideo();
}
window.onYouTubeIframeAPIReady = () => attach();
function attach() {
  if (player || !desired || !window.YT || !YT.Player) return;
  const iframe = document.createElement('iframe'); iframe.id = 'player';
  iframe.title = 'YouTube'; iframe.allow = 'autoplay; encrypted-media; picture-in-picture';
  iframe.setAttribute('sandbox', 'allow-scripts allow-same-origin allow-presentation');
  iframe.referrerPolicy = 'strict-origin-when-cross-origin';
  iframe.src = 'https://www.youtube-nocookie.com/embed/' + desired.video + '?enablejsapi=1&playsinline=1&origin=' + encodeURIComponent(location.origin);
  document.getElementById('surface').replaceWith(iframe);
  player = new YT.Player('player', {events: {
    onReady() { ready = true; report('ready'); notice.textContent = 'Click the preview to enable playback / Kliknij podgląd, aby włączyć film'; sync(); },
    onStateChange(event) { report(({1:'playing',2:'paused',3:'buffering',0:'ended'})[event.data] || 'ready'); },
    onAutoplayBlocked() { report('blocked'); enable.hidden = false; },
    onError(event) { report([2,5,100,101,150,153].includes(event.data) ? 'error_' + event.data : 'error');
      notice.hidden = false; notice.textContent = 'YouTube error ' + event.data + ' · film unavailable / film niedostępny'; }
  }});
}
enable.addEventListener('click', () => {
  if (!ready) return;
  unlocked = true; enable.hidden = true; notice.hidden = true;
  player.unMute(); player.playVideo(); sync();
});
async function poll() {
  try {
    const response = await fetch('state', {cache:'no-store'});
    if (!response.ok) throw new Error('session closed');
    const next = await response.json();
    if (!/^[A-Za-z0-9_-]{11}$/.test(next.video) || !Number.isFinite(next.position) || next.position < 0
        || typeof next.playing !== 'boolean' || !Number.isSafeInteger(next.revision) || next.revision < 0
        || !Number.isInteger(next.volume) || next.volume < 0 || next.volume > 100) throw new Error('invalid state');
    desired = next; receivedAt = performance.now(); attach(); sync();
  } catch {
    if (ready) player.pauseVideo();
    notice.hidden = false; notice.textContent = 'Connection interrupted / Połączenie przerwane';
  } finally { setTimeout(poll, 500); }
}
poll();
