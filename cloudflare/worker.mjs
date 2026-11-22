import { DurableObject } from 'cloudflare:workers';
import { Registry, RegistryError } from './registry.mjs';

const headers = { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store',
  'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'no-referrer' };
const reply = (value, status = 200) => new Response(JSON.stringify(value), { status, headers });
const hash = async text => Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',
  new TextEncoder().encode(text))), n => n.toString(16).padStart(2, '0')).join('');
const random = {
  token: () => btoa(String.fromCharCode(...crypto.getRandomValues(new Uint8Array(32))))
    .replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', ''),
  code: () => {
    // Rejection sampling avoids modulo bias and preserves leading zeroes.
    let n; do { n = crypto.getRandomValues(new Uint32Array(1))[0]; } while (n >= 4294000000);
    return String(n % 1000000).padStart(6, '0');
  }
};

export default {
  async fetch(request, env) {
    if (request.method === 'GET' && new URL(request.url).pathname === '/health')
      return reply({ service: 'minecraft-party-codes', protocol: 1, autoJoin: true, publicLobbies: true });
    if (request.method !== 'POST' || !/^\/v1\/(create|request|poll|host|decide|remove|list)$/.test(new URL(request.url).pathname))
      return reply({ error: 'not_found' }, 404);
    if (request.headers.has('Origin')) return reply({ error: 'forbidden' }, 403);
    const length = Number(request.headers.get('Content-Length'));
    if (!Number.isSafeInteger(length) || length < 2 || length > 8192) return reply({ error: 'size' }, 413);
    try {
      const reader = request.body.getReader(); let total = 0; const parts = [];
      while (true) {
        const { done, value } = await reader.read(); if (done) break;
        total += value.length; if (total > 8192) { await reader.cancel(); return reply({ error: 'size' }, 413); }
        parts.push(value);
      }
      const bytes = new Uint8Array(total); let offset = 0;
      for (const part of parts) { bytes.set(part, offset); offset += part.length; }
      const body = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes));
      if (!body || Array.isArray(body) || typeof body !== 'object') return reply({ error: 'body' }, 400);
      delete body.tokenHash;
      if (['request', 'poll'].includes(new URL(request.url).pathname.split('/').pop())) {
        if (!/^[A-Za-z0-9_-]{43}$/.test(body.requestToken || '')) return reply({ error: 'token' }, 400);
        body.tokenHash = await hash(body.requestToken); delete body.requestToken;
      }
      const ip = await hash(request.headers.get('CF-Connecting-IP') || 'unknown');
      const stub = env.DIRECTORY.get(env.DIRECTORY.idFromName('directory-v1'));
      return await stub.fetch(new Request(request.url, { method: 'POST', body: JSON.stringify({ body, ip }) }));
    } catch { return reply({ error: 'unavailable' }, 503); }
  }
};

export class PartyDirectory extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.sql = ctx.storage.sql;
    this.sql.exec('CREATE TABLE IF NOT EXISTS entries (key TEXT PRIMARY KEY, value TEXT NOT NULL, expires INTEGER NOT NULL)');
    this.sql.exec('CREATE INDEX IF NOT EXISTS expiry ON entries(expires)');
    this.registry = new Registry({
      get: key => { const row = [...this.sql.exec('SELECT value FROM entries WHERE key = ?', key)][0]; return row ? JSON.parse(row.value) : undefined; },
      put: (key, value, expiry) => this.sql.exec('INSERT OR REPLACE INTO entries VALUES (?, ?, ?)', key, JSON.stringify(value), expiry),
      del: key => this.sql.exec('DELETE FROM entries WHERE key = ?', key),
      rooms: () => [...this.sql.exec("SELECT count(*) AS count FROM entries WHERE key LIKE 'room:%'")][0].count,
      list: () => [...this.sql.exec("SELECT key, value FROM entries WHERE key LIKE 'room:%' LIMIT 256")]
        .map(row => ({ code: row.key.slice(5), room: JSON.parse(row.value) })),
      clean: now => this.sql.exec('DELETE FROM entries WHERE expires <= ?', now)
    }, random);
  }
  async fetch(request) {
    return this.ctx.blockConcurrencyWhile(async () => {
      try {
        const { body, ip } = await request.json();
        const result = this.registry.handle(new URL(request.url).pathname.split('/').pop(), body, ip);
        if (await this.ctx.storage.getAlarm() === null) await this.ctx.storage.setAlarm(Date.now() + 120000);
        return reply(result);
      } catch (e) { return reply({ error: 'request_failed' }, e instanceof RegistryError ? e.status : 503); }
    });
  }
  async alarm() {
    this.sql.exec('DELETE FROM entries WHERE expires <= ?', Date.now());
    if ([...this.sql.exec('SELECT count(*) AS count FROM entries')][0].count > 0)
      await this.ctx.storage.setAlarm(Date.now() + 120000);
  }
}
