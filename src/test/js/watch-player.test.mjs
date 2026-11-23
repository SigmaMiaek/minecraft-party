import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFileSync} from 'node:fs';

// Unit tests for official IFrame API commands; these do not pretend to play or decode a video.
const script = readFileSync(new URL('../../main/resources/assets/minecraftparty/watch/player.js', import.meta.url), 'utf8');
async function fixture() {
  let now = 0, readyHandler;
  const calls = [], nodes = new Map(), base = {video:'M7lc1UVf-VE', revision:1, position:12, playing:false, volume:60};
  const player = {state:2, time:12, getPlayerState(){return this.state}, getCurrentTime(){return this.time},
    setVolume(n){calls.push(['volume',n])}, seekTo(n){calls.push(['seek',n])},
    playVideo(){calls.push(['play']);this.state=1}, pauseVideo(){calls.push(['pause']);this.state=2}, unMute(){calls.push(['unmute'])}};
  function node() {return {hidden:false,textContent:'',setAttribute(){},addEventListener(name,fn){this[name]=fn},replaceWith(){}};}
  for (const id of ['notice','enable','surface']) nodes.set(id,node());
  const context = vm.createContext({document:{getElementById:id=>nodes.get(id),createElement:node},
    window:{YT:true}, YT:{Player:function(id,options){readyHandler=options.events.onReady;return player}},
    location:{origin:'http://127.0.0.1:1234'}, performance:{now:()=>now},
    fetch:async path=>path==='state'?{ok:true,json:async()=>base}:{ok:true}, setTimeout(){}, encodeURIComponent});
  vm.runInContext(script,context); await new Promise(resolve=>setImmediate(resolve)); readyHandler();
  return {calls,player,enable:nodes.get('enable'),set(next,time=now){now=time;vm.runInContext('desired='+JSON.stringify({...base,...next})+'; receivedAt=performance.now(); sync();',context)},sync(time){now=time;vm.runInContext('sync()',context)}};
}
test('playback and sound require an explicit viewer gesture', async()=>{
  const f=await fixture(); assert.deepEqual(f.calls,[]);
  f.enable.click(); assert.ok(f.calls.some(c=>c[0]==='unmute'));
  assert.equal(f.player.state,2); // Host starts paused, even after enabling the player.
});
test('host pause play and seeks control the official player API',async()=>{
  const f=await fixture();f.enable.click();f.calls.length=0;
  f.set({playing:true,position:65,revision:2});
  assert.ok(f.calls.some(c=>c[0]==='seek'&&c[1]===65)); assert.equal(f.player.state,1);
  f.set({playing:false,position:68,revision:3}); assert.equal(f.player.state,2);
  assert.ok(f.calls.some(c=>c[0]==='seek'&&c[1]===68));
});
test('clock drift is corrected with bounded seek frequency',async()=>{
  const f=await fixture();f.enable.click();f.set({playing:true,position:10,revision:2});f.calls.length=0;
  f.player.time=0;f.sync(1000);assert.equal(f.calls.filter(c=>c[0]==='seek').length,0);
  f.sync(4000);assert.equal(f.calls.filter(c=>c[0]==='seek').length,1);
  assert.equal(f.calls.find(c=>c[0]==='seek')[1],14);
  f.sync(4500);assert.equal(f.calls.filter(c=>c[0]==='seek').length,1);
});
test('ended videos stay ended until the host changes playback',async()=>{
  const f=await fixture();f.enable.click();f.player.state=0;f.calls.length=0;
  f.set({playing:true});assert.equal(f.calls.filter(c=>c[0]==='play'||c[0]==='seek').length,0);
  f.set({playing:true,position:0,revision:2});assert.ok(f.calls.some(c=>c[0]==='play'));
});
