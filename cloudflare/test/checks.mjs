import { Registry, RegistryError } from '../registry.mjs';

export function runChecks() {
  const passed = [];
  function assert(value, message) { if (!value) throw new Error(message); }
  function test(name, action) { action(); passed.push(name); }
  function fixture(autoJoin = false) {
    let now = 1000000, next = 1; const data = new Map();
    const store = {
      get: key => data.has(key) ? structuredClone(data.get(key).value) : undefined,
      put: (key, value, expires) => data.set(key, { value: structuredClone(value), expires }),
      del: key => data.delete(key),
      rooms: () => [...data.keys()].filter(k => k.startsWith('room:')).length,
      list: () => [...data.entries()].filter(([key]) => key.startsWith('room:'))
        .map(([key, entry]) => ({ code: key.slice(5), room: structuredClone(entry.value) })),
      clean: time => { for (const [k, v] of data) if (v.expires <= time) data.delete(k); }
    };
    const random = { token: () => String(next++).padStart(43, 'x'), code: () => String(next++).padStart(6, '0') };
    const registry = new Registry(store, random, () => now);
    const call = (action, body, ip = 'test') => registry.handle(action, body, ip);
    const room = call('create', { invite: 'mp3:' + 'A'.repeat(100), expiresAt: now + 3600000, autoJoin });
    const owner = { code: room.code, owner: room.owner };
    const guest = { code: room.code, name: 'Friend', tokenHash: 'a'.repeat(64) };
    return { call, room, owner, guest, random, store, advance: n => now += n };
  }
  function rejected(action, status) {
    try { action(); } catch (e) { assert(e instanceof RegistryError && e.status === status, `Expected ${status}, got ${e.status}`); return; }
    throw new Error('Unauthorized operation succeeded');
  }
  test('six digits preserve leading zeroes', () => { const f = fixture(); assert(/^0\d{5}$/.test(f.room.code), 'code'); });
  test('explicit auto-join returns the invite without a host decision', () => {
    const f = fixture(true), request = f.call('request', f.guest);
    assert(f.room.autoJoin === true && request.status === 'approved' && request.invite.startsWith('mp3:'), 'auto join unavailable');
    assert(!('owner' in request) && f.call('host', f.owner).pending.length === 0, 'owner leaked or approval required');
    f.call('remove', f.owner); rejected(() => f.call('poll', f.guest), 404);
  });
  test('guessing a code does not release the invite', () => {
    const f = fixture(), r = f.call('request', { ...f.guest, autoJoin: true, allow: true });
    assert(r.status === 'pending' && !('invite' in r) && !('owner' in r), 'leaked invite');
    const poll = f.call('poll', f.guest); assert(!('invite' in poll), 'leaked on poll');
  });
  test('only owner can inspect or approve requests', () => {
    const f = fixture(); f.call('request', f.guest);
    rejected(() => f.call('host', { ...f.owner, owner: 'z'.repeat(43) }), 403);
    rejected(() => f.call('decide', { ...f.owner, owner: 'z'.repeat(43), allow: true }), 403);
    rejected(() => f.call('remove', { ...f.owner, owner: 'z'.repeat(43) }), 403);
  });
  test('approval releases invite to only the requesting capability', () => {
    const f = fixture(), request = f.call('request', f.guest);
    const list = f.call('host', f.owner).pending;
    assert(list.length === 1 && list[0].check === request.check && !('tokenHash' in list[0]), 'host listing');
    f.call('decide', { ...f.owner, id: list[0].id, allow: true });
    assert(f.call('poll', f.guest).invite.startsWith('mp3:'), 'approved invite missing');
    rejected(() => f.call('poll', { ...f.guest, tokenHash: 'b'.repeat(64) }), 404);
  });
  test('denial releases no invite', () => {
    const f = fixture(); f.call('request', f.guest);
    f.call('decide', { ...f.owner, id: f.call('host', f.owner).pending[0].id, allow: false });
    const r = f.call('poll', f.guest); assert(r.status === 'denied' && !('invite' in r), 'denied leak');
  });
  test('request is idempotent and cannot rename another request', () => {
    const f = fixture(), a = f.call('request', f.guest), b = f.call('request', { ...f.guest, name: 'Impostor' });
    const pending = f.call('host', f.owner).pending;
    assert(a.check === b.check && pending.length === 1 && pending[0].name === 'Friend', 'duplicate request');
  });
  test('code expires after host disappears', () => {
    const f = fixture(); f.advance(120001); rejected(() => f.call('request', f.guest), 404);
  });
  test('heartbeat extends lease without extending hard expiry', () => {
    const f = fixture(); f.advance(100000); f.call('host', f.owner); f.advance(100000);
    assert(f.call('request', f.guest).status === 'pending', 'lease not extended');
    f.advance(3600000); rejected(() => f.call('host', f.owner), 404);
  });
  test('request expires even with host online', () => {
    const f = fixture(); f.call('request', f.guest); f.advance(91000); f.call('host', f.owner);
    rejected(() => f.call('poll', f.guest), 404);
  });
  test('revocation deletes pending and approved invitations', () => {
    const f = fixture(); f.call('request', f.guest); f.call('remove', f.owner);
    rejected(() => f.call('poll', f.guest), 404);
  });
  test('create collisions do not replace a live host', () => {
    const f = fixture(); f.random.code = () => f.room.code;
    rejected(() => f.call('create', { invite: 'mp3:' + 'B'.repeat(100), expiresAt: 1500000 }, 'other'), 503);
    assert(f.call('host', f.owner).pending.length === 0, 'room replaced');
  });
  test('rate limit survives new Registry instances and resets after window', () => {
    const f = fixture(); for (let i = 0; i < 10; i++) f.call('request', f.guest);
    rejected(() => f.call('request', f.guest), 429);
    const r = new Registry(f.store, f.random, () => 1000000);
    rejected(() => r.handle('request', f.guest, 'test'), 429);
    f.advance(61000); assert(f.call('request', f.guest).status === 'pending', 'rate limit stuck');
  });
  test('malformed input, direct invites and injected names are rejected', () => {
    const f = fixture();
    rejected(() => f.call('create', { invite: 'mp2:' + 'A'.repeat(100), expiresAt: 1500000 }), 400);
    rejected(() => f.call('request', { ...f.guest, code: '../123456' }), 400);
    rejected(() => f.call('request', { ...f.guest, name: 'x\nOperator' }), 400);
    rejected(() => f.call('request', { ...f.guest, tokenHash: '' }), 400);
  });
  const metadata = { name: 'Public Party', host: 'Host', players: 1, limit: 6, inWorld: false, minecraft: '26.2', protocol: 4 };
  const publish = (f, extra = {}) => f.call('create', { invite: 'mp3:' + 'B'.repeat(100), expiresAt: 1500000,
    autoJoin: true, publicLobby: true, listing: { ...metadata, ...extra } });
  test('old and new private rooms never appear in the public directory', () => {
    const f = fixture(true);
    assert(f.room.publicLobby === false && f.call('list', {}).lobbies.length === 0, 'private room exposed');
    rejected(() => f.call('host', { ...f.owner, listing: metadata, publicLobby: true }), 400);
    assert(f.call('list', {}).lobbies.length === 0, 'private room was silently made public');
  });
  test('public discovery exposes only a deliberate join code and bounded metadata', () => {
    const f = fixture(true), room = publish(f, { invite: 'secret', owner: 'secret', ip: 'secret' });
    const visible = f.call('list', {}).lobbies;
    assert(visible.length === 1 && visible[0].code === room.code && visible[0].name === metadata.name, 'listing missing');
    assert(Object.keys(visible[0]).sort().join() === 'code,host,inWorld,limit,minecraft,name,players,protocol', 'secret or arbitrary field exposed');
    assert(f.call('request', { ...f.guest, code: room.code }).status === 'approved', 'public join requires approval');
  });
  test('only the owner can update public counts and revocation removes the listing', () => {
    const f = fixture(), room = publish(f), owner = { code: room.code, owner: room.owner };
    rejected(() => f.call('host', { ...owner, owner: 'z'.repeat(43), listing: { ...metadata, players: 5 } }), 403);
    f.call('host', { ...owner, listing: { ...metadata, players: 4, inWorld: true } });
    assert(f.call('list', {}).lobbies[0].players === 4 && f.call('list', {}).lobbies[0].inWorld, 'metadata not refreshed');
    f.call('remove', owner); assert(f.call('list', {}).lobbies.length === 0, 'closed room remains public');
  });
  test('public listings expire after a missing host heartbeat', () => {
    const f = fixture(); publish(f); f.advance(120001);
    assert(f.call('list', {}).lobbies.length === 0, 'stale host stays visible');
  });
  test('public directory uses bounded pages and never includes private entries', () => {
    const f = fixture(); for (let i = 0; i < 3; i++) publish(f, { name: 'Room ' + i });
    const first = f.call('list', { page: 0, size: 2 }), second = f.call('list', { page: 1, size: 2 });
    assert(first.pages === 2 && first.lobbies.length === 2 && second.lobbies.length === 1, 'pagination');
    assert(new Set([...first.lobbies, ...second.lobbies].map(x => x.code)).size === 3, 'duplicate or private row');
    for (const body of [{ page: -1 }, { page: 256 }, { size: 17 }, { size: 0 }, { page: '0' }]) rejected(() => f.call('list', body), 400);
  });
  test('public metadata rejects formatting, invalid capacity and unsupported protocols', () => {
    for (const extra of [{ name: 'bad\nname' }, { name: '§kHidden' }, { name: '\u202eRTL' }, { host: 'x y' },
      { players: 0 }, { players: 9 }, { limit: 1 }, { protocol: 3 }, { minecraft: 'other' }, { inWorld: 'yes' }])
      rejected(() => publish(fixture(), extra), 400);
    for (const publicLobby of ['true', null, 1]) {
      const f = fixture();
      rejected(() => f.call('create', { invite: 'mp3:' + 'B'.repeat(100), expiresAt: 1500000, publicLobby }), 400);
    }
  });
  test('public list requests are rate limited', () => {
    const f = fixture(); for (let i = 0; i < 60; i++) f.call('list', {});
    rejected(() => f.call('list', {}), 429);
  });
  test('ten-player rooms are discoverable only by compatible clients', () => {
    const f = fixture(), old = publish(f), modern = publish(f, { protocol: 5, limit: 10, players: 10 });
    const legacy = f.call('list', {}), current = f.call('list', { protocol: 5 });
    assert(legacy.protocol === 4 && legacy.lobbies.length === 1 && legacy.lobbies[0].code === old.code, 'legacy isolation');
    assert(current.protocol === 5 && current.lobbies.length === 1 && current.lobbies[0].code === modern.code
      && current.lobbies[0].limit === 10 && current.lobbies[0].players === 10, 'ten-player room missing');
    f.call('remove', { code: modern.code, owner: modern.owner });
    assert(f.call('list', { protocol: 5 }).lobbies.length === 0, 'modern room not removed');
    assert(f.call('list', {}).lobbies.length === 1, 'legacy room lost');
  });
  test('both protocol lists preserve privacy and enforce their own capacity limits', () => {
    const f = fixture(true);
    assert(f.call('list', { protocol: 5 }).lobbies.length === 0, 'private room exposed to modern clients');
    for (const extra of [{ protocol: 4, limit: 9 }, { protocol: 5, limit: 11 }, { protocol: 5, limit: 10, players: 11 }])
      rejected(() => publish(fixture(), extra), 400);
    for (const protocol of [3, 6, '5', true]) rejected(() => f.call('list', { protocol }), 400);
  });
  return passed;
}
