import { Registry, RegistryError } from '../registry.mjs';

export function runChecks() {
  const passed = [];
  function assert(value, message) { if (!value) throw new Error(message); }
  function test(name, action) { action(); passed.push(name); }
  function fixture() {
    let now = 1000000, next = 1; const data = new Map();
    const store = {
      get: key => data.has(key) ? structuredClone(data.get(key).value) : undefined,
      put: (key, value, expires) => data.set(key, { value: structuredClone(value), expires }),
      del: key => data.delete(key),
      rooms: () => [...data.keys()].filter(k => k.startsWith('room:')).length,
      clean: time => { for (const [k, v] of data) if (v.expires <= time) data.delete(k); }
    };
    const random = { token: () => String(next++).padStart(43, 'x'), code: () => String(next++).padStart(6, '0') };
    const registry = new Registry(store, random, () => now);
    const call = (action, body, ip = 'test') => registry.handle(action, body, ip);
    const room = call('create', { invite: 'mp3:' + 'A'.repeat(100), expiresAt: now + 3600000 });
    const owner = { code: room.code, owner: room.owner };
    const guest = { code: room.code, name: 'Friend', tokenHash: 'a'.repeat(64) };
    return { call, room, owner, guest, random, store, advance: n => now += n };
  }
  function rejected(action, status) {
    try { action(); } catch (e) { assert(e instanceof RegistryError && e.status === status, `Expected ${status}, got ${e.status}`); return; }
    throw new Error('Unauthorized operation succeeded');
  }
  test('six digits preserve leading zeroes', () => { const f = fixture(); assert(/^0\d{5}$/.test(f.room.code), 'code'); });
  test('guessing a code does not release the invite', () => {
    const f = fixture(), r = f.call('request', f.guest);
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
  return passed;
}
