// No network calls here. All access is serialized by the Durable Object adapter.
export class RegistryError extends Error {
  constructor(status) { super('Request rejected'); this.status = status; }
}
const fail = status => { throw new RegistryError(status); };
const tokenPattern = /^[A-Za-z0-9_-]{43}$/;
export class Registry {
  constructor(store, random, now = () => Date.now()) {
    this.store = store; this.random = random; this.now = now;
  }
  rate(ip, action) {
    const minute = Math.floor(this.now() / 60000);
    const limit = { create: 5, request: 10, poll: 90, host: 90, decide: 30, remove: 30 }[action];
    if (!limit) fail(404);
    for (const [key, maximum] of [[`rate:${minute}:all`, 600], [`rate:${minute}:${ip}:${action}`, limit]]) {
      const count = this.store.get(key) || 0;
      if (count >= maximum) fail(429);
      this.store.put(key, count + 1, (minute + 2) * 60000);
    }
  }
  handle(action, body, ip) {
    this.store.clean(this.now());
    this.rate(ip, action);
    const now = this.now();
    if (action === 'create') {
      if (typeof body.invite !== 'string' || !/^mp3:[A-Za-z0-9_-]{20,4092}$/.test(body.invite)
          || !Number.isSafeInteger(body.expiresAt) || body.expiresAt <= now || body.expiresAt > now + 3600000) fail(400);
      if (this.store.rooms() >= 256) fail(503);
      let code;
      for (let attempt = 0; attempt < 20; attempt++) {
        const candidate = this.random.code();
        if (!this.store.get('room:' + candidate)) { code = candidate; break; }
      }
      if (!code) fail(503);
      const room = { owner: this.random.token(), invite: body.invite, hardExpiry: body.expiresAt,
        expiresAt: Math.min(body.expiresAt, now + 120000), requests: [] };
      this.save(code, room);
      return { code, owner: room.owner, expiresAt: room.expiresAt };
    }
    if (typeof body.code !== 'string' || !/^\d{6}$/.test(body.code)) fail(400);
    const room = this.store.get('room:' + body.code);
    if (!room || room.expiresAt <= now) fail(404);
    room.requests = room.requests.filter(r => r.expiresAt > now);
    if (['host', 'decide', 'remove'].includes(action)) {
      if (!tokenPattern.test(body.owner || '') || body.owner !== room.owner) fail(403);
      if (action === 'remove') { this.store.del('room:' + body.code); return { status: 'removed' }; }
      if (action === 'decide') {
        const request = room.requests.find(r => r.id === body.id && r.status === 'pending');
        if (!request || typeof body.allow !== 'boolean') fail(404);
        request.status = body.allow ? 'approved' : 'denied';
      }
      room.expiresAt = Math.min(room.hardExpiry, now + 120000);
      this.save(body.code, room);
      return { expiresAt: room.expiresAt, pending: room.requests.filter(r => r.status === 'pending')
        .map(r => ({ id: r.id, name: r.name, check: r.check })) };
    }
    // The adapter hashes the guest's random capability before storing or matching it.
    if (typeof body.tokenHash !== 'string' || !/^[0-9a-f]{64}$/.test(body.tokenHash)) fail(400);
    let request = room.requests.find(r => r.tokenHash === body.tokenHash);
    if (action === 'request' && !request) {
      if (typeof body.name !== 'string' || !/^[A-Za-z0-9_]{1,16}$/.test(body.name)) fail(400);
      if (room.requests.length >= 12) fail(429);
      request = { id: this.random.token(), tokenHash: body.tokenHash, name: body.name,
        check: this.random.code(), status: 'pending', expiresAt: Math.min(room.expiresAt, now + 90000) };
      room.requests.push(request); this.save(body.code, room);
    }
    if (!request) fail(404);
    const reply = { status: request.status, check: request.check, expiresAt: request.expiresAt };
    if (request.status === 'approved') reply.invite = room.invite;
    return reply;
  }
  save(code, room) { this.store.put('room:' + code, room, room.expiresAt); }
}
