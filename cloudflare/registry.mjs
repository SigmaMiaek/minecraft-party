// No network calls here. All access is serialized by the Durable Object adapter.
export class RegistryError extends Error {
  constructor(status) { super('Request rejected'); this.status = status; }
}
const fail = status => { throw new RegistryError(status); };
const tokenPattern = /^[A-Za-z0-9_-]{43}$/;
function listing(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)
      || typeof value.name !== 'string' || !value.name.trim() || value.name.length > 40
      || /[\p{Cc}\p{Cf}\p{Cs}\u00a7]/u.test(value.name)
      || typeof value.host !== 'string' || !/^[A-Za-z0-9_]{1,16}$/.test(value.host)
      || !Number.isSafeInteger(value.limit) || value.limit < 2 || value.limit > (value.protocol >= 5 ? 10 : 8)
      || !Number.isSafeInteger(value.players) || value.players < 1 || value.players > value.limit
      || typeof value.inWorld !== 'boolean' || value.minecraft !== '26.2' || ![4, 5, 6, 7].includes(value.protocol)) fail(400);
  // Copy only the deliberately public fields; never retain arbitrary client keys.
  return { name: value.name, host: value.host, players: value.players, limit: value.limit,
    inWorld: value.inWorld, minecraft: '26.2', protocol: value.protocol };
}
export class Registry {
  constructor(store, random, now = () => Date.now()) {
    this.store = store; this.random = random; this.now = now;
  }
  rate(ip, action) {
    const minute = Math.floor(this.now() / 60000);
    const limit = { create: 5, request: 10, poll: 90, host: 90, decide: 30, remove: 30, list: 60 }[action];
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
    if (action === 'list') {
      const page = body.page ?? 0, size = body.size ?? 8, protocol = body.protocol ?? 4;
      if (![4, 5, 6, 7].includes(protocol)) fail(400);
      if (!Number.isSafeInteger(page) || page < 0 || page > 255 || !Number.isSafeInteger(size) || size < 1 || size > 16) fail(400);
      const rooms = this.store.list().filter(entry => entry.room.publicLobby === true && entry.room.expiresAt > now
          && entry.room.listing?.protocol === protocol && entry.room.autoJoin === true).sort((a, b) => a.code.localeCompare(b.code));
      return { publicLobbies: true, protocol, page, pages: Math.max(1, Math.ceil(rooms.length / size)),
        lobbies: rooms.slice(page * size, (page + 1) * size).map(entry => ({ code: entry.code, ...entry.room.listing })) };
    }
    if (action === 'create') {
      if (typeof body.invite !== 'string' || !/^mp3:[A-Za-z0-9_-]{20,4092}$/.test(body.invite)
          || !Number.isSafeInteger(body.expiresAt) || body.expiresAt <= now || body.expiresAt > now + 3600000) fail(400);
      if (body.publicLobby !== undefined && typeof body.publicLobby !== 'boolean') fail(400);
      const publicLobby = body.publicLobby === true;
      if (publicLobby && body.autoJoin !== true) fail(400);
      const metadata = publicLobby ? listing(body.listing) : null;
      if (this.store.rooms() >= 256) fail(503);
      let code;
      for (let attempt = 0; attempt < 20; attempt++) {
        const candidate = this.random.code();
        if (!this.store.get('room:' + candidate)) { code = candidate; break; }
      }
      if (!code) fail(503);
      const room = { owner: this.random.token(), invite: body.invite, hardExpiry: body.expiresAt,
        autoJoin: body.autoJoin === true, publicLobby, listing: metadata,
        expiresAt: Math.min(body.expiresAt, now + 120000), requests: [] };
      this.save(code, room);
      return { code, owner: room.owner, expiresAt: room.expiresAt, autoJoin: room.autoJoin, publicLobby };
    }
    if (typeof body.code !== 'string' || !/^\d{6}$/.test(body.code)) fail(400);
    const room = this.store.get('room:' + body.code);
    if (!room || room.expiresAt <= now) fail(404);
    room.requests = room.requests.filter(r => r.expiresAt > now);
    if (['host', 'decide', 'remove'].includes(action)) {
      if (!tokenPattern.test(body.owner || '') || body.owner !== room.owner) fail(403);
      if (action === 'remove') { this.store.del('room:' + body.code); return { status: 'removed' }; }
      if (action === 'host' && body.listing !== undefined) {
        if (room.publicLobby !== true) fail(400);
        room.listing = listing(body.listing);
      }
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
        check: this.random.code(), status: room.autoJoin ? 'approved' : 'pending', expiresAt: Math.min(room.expiresAt, now + 90000) };
      room.requests.push(request); this.save(body.code, room);
    }
    if (!request) fail(404);
    const reply = { status: request.status, check: request.check, expiresAt: request.expiresAt };
    if (request.status === 'approved') reply.invite = room.invite;
    return reply;
  }
  save(code, room) { this.store.put('room:' + code, room, room.expiresAt); }
}
