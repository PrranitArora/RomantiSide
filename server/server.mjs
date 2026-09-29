import http from 'node:http';
import { randomBytes, randomUUID, createHash } from 'node:crypto';
import { existsSync, mkdirSync, openSync, readFileSync, writeFileSync, fsyncSync, closeSync, renameSync, unlinkSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const DAY = 86_400_000;
const MAX_BODY = 65_536;
const MAX_FILE = 32 * 1024 * 1024;
const MAX_PROFILES = 500;
const MAX_EVENTS = 100_000;
const MAX_PROFILE_EVENTS = 20_000;
const MAX_FRIENDS = 100;
const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const defaultDataFile = fileURLToPath(new URL('./data/state.json', import.meta.url));
const hash = value => createHash('sha256').update(value).digest('hex');
const utcDay = at => new Date(at).toISOString().slice(0, 10);

class ApiError extends Error {
  constructor(status, code, message) { super(message); this.status = status; this.code = code; }
}
const fail = (status, code, message) => { throw new ApiError(status, code, message); };
function exactObject(value, keys) {
  if (!value || typeof value !== 'object' || Array.isArray(value)
      || Object.keys(value).some(key => !keys.includes(key))) {
    fail(400, 'invalid_body', 'Use only the documented JSON fields.');
  }
}
function nameValue(value) {
  if (typeof value !== 'string') fail(400, 'invalid_name', 'Choose a display name of 1 to 32 characters.');
  const name = value.trim().normalize('NFC');
  if (!name || [...name].length > 32 || Buffer.byteLength(name) > 128
      || /[\u0000-\u001f\u007f-\u009f\u202a-\u202e\u2066-\u2069]/u.test(name)) {
    fail(400, 'invalid_name', 'Choose a display name of 1 to 32 characters without control characters.');
  }
  return name;
}
function codeValue(value) {
  if (typeof value !== 'string') fail(400, 'invalid_code', 'Enter the private 12-character friendship code.');
  const code = value.trim().toUpperCase();
  if (!/^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{12}$/.test(code)) {
    fail(400, 'invalid_code', 'Enter the private 12-character friendship code.');
  }
  return code;
}
function newCode() {
  // Rejection sampling keeps the alphabet distribution uniform.
  let value = '';
  while (value.length < 12) {
    for (const byte of randomBytes(24)) {
      const ceiling = 256 - (256 % CODE_ALPHABET.length);
      if (byte < ceiling) value += CODE_ALPHABET[byte % CODE_ALPHABET.length];
      if (value.length === 12) break;
    }
  }
  return value;
}

class Store {
  constructor(file) {
    this.file = path.resolve(file);
    mkdirSync(path.dirname(this.file), { recursive: true, mode: 0o700 });
    this.data = { version: 1, profiles: [] };
    if (existsSync(this.file)) {
      if (statSync(this.file).size > MAX_FILE) throw new Error('Stored data exceeds the prototype limit.');
      this.data = JSON.parse(readFileSync(this.file, 'utf8'));
      if (this.data.version !== 1 || !Array.isArray(this.data.profiles)
          || this.data.profiles.some(p => !p.id || !p.tokenHash || !p.code || !Array.isArray(p.events) || !Array.isArray(p.friends))) {
        throw new Error('Stored data has an unsupported structure. Preserve the file and investigate.');
      }
    }
  }
  change(edit) {
    const next = structuredClone(this.data);
    const result = edit(next);
    const encoded = JSON.stringify(next);
    if (Buffer.byteLength(encoded) > MAX_FILE) fail(507, 'capacity', 'This demo server has reached its storage limit.');
    const temporary = `${this.file}.${randomBytes(8).toString('hex')}.tmp`;
    let descriptor;
    try {
      descriptor = openSync(temporary, 'wx', 0o600);
      writeFileSync(descriptor, encoded, 'utf8');
      fsyncSync(descriptor);
      closeSync(descriptor); descriptor = undefined;
      renameSync(temporary, this.file);
      // Directory fsync is supported on POSIX; Windows can reject directory handles.
      try { const directory = openSync(path.dirname(this.file), 'r'); try { fsyncSync(directory); } finally { closeSync(directory); } } catch {}
      this.data = next;
      return result;
    } finally {
      if (descriptor !== undefined) closeSync(descriptor);
      if (existsSync(temporary)) unlinkSync(temporary);
    }
  }
}

function readBody(request) {
  if (!/^application\/json(?:\s*;.*)?$/i.test(request.headers['content-type'] || '')) {
    fail(415, 'content_type', 'Send application/json.');
  }
  return new Promise((resolve, reject) => {
    let length = 0, tooLarge = false;
    const chunks = [];
    request.on('data', chunk => {
      length += chunk.length;
      if (length > MAX_BODY) {
        if (!tooLarge) reject(new ApiError(413, 'body_too_large', 'Keep request bodies below 64 KiB.'));
        tooLarge = true; chunks.length = 0;
      } else if (!tooLarge) chunks.push(chunk);
    });
    request.on('end', () => {
      if (tooLarge) return;
      try { resolve(JSON.parse(Buffer.concat(chunks).toString('utf8'))); }
      catch { reject(new ApiError(400, 'invalid_json', 'Send a valid JSON object.')); }
    });
    request.on('error', reject);
    request.on('aborted', () => reject(new ApiError(400, 'aborted', 'Request was interrupted.')));
  });
}

function limiter(clock) {
  const buckets = new Map();
  return (key, maximum, duration) => {
    const current = clock();
    if (buckets.size > 10_000) {
      for (const [id, bucket] of buckets) if (bucket.until <= current) buckets.delete(id);
      if (buckets.size > 10_000) fail(503, 'busy', 'Server is busy. Try later.');
    }
    let bucket = buckets.get(key);
    if (!bucket || bucket.until <= current) {
      bucket = { count: 0, until: current + duration }; buckets.set(key, bucket);
    }
    if (++bucket.count > maximum) fail(429, 'rate_limit', 'Too many requests. Please wait before trying again.');
  };
}
function send(response, status, value) {
  if (response.destroyed || response.writableEnded) return;
  response.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
    ...(status === 429 ? { 'Retry-After': '60' } : {})
  });
  response.end(JSON.stringify(value));
}

export function createServer({ dataFile = process.env.DATA_FILE || defaultDataFile, now = () => Date.now() } = {}) {
  const store = new Store(dataFile);
  const limit = limiter(now);
  const authenticate = request => {
    const match = /^Bearer ([A-Za-z0-9_-]{43})$/.exec(request.headers.authorization || '');
    const profile = match && store.data.profiles.find(p => p.tokenHash === hash(match[1]));
    if (!profile) fail(401, 'unauthorized', 'A valid profile token is required.');
    limit(`profile:${profile.id}`, 90, 60_000);
    return profile;
  };
  const server = http.createServer(async (request, response) => {
    try {
      // Deliberately do not trust X-Forwarded-For. Default host is loopback only.
      const address = request.socket.remoteAddress || 'unknown';
      limit(`ip:${address}`, 240, 60_000);
      const url = new URL(request.url, 'http://localhost');
      if (request.method === 'GET' && url.pathname === '/health') {
        send(response, 200, { ok: true, service: 'tiny-wonder-friends-demo' }); return;
      }
      if (request.method === 'POST' && url.pathname === '/v1/profile') {
        limit(`create:${address}`, 10, 3_600_000);
        const body = await readBody(request); exactObject(body, ['displayName']);
        const displayName = nameValue(body.displayName);
        if (store.data.profiles.length >= MAX_PROFILES) fail(507, 'capacity', 'This demo server has reached its profile limit.');
        const token = randomBytes(32).toString('base64url');
        let code; do { code = newCode(); } while (store.data.profiles.some(p => p.code === code));
        store.change(data => data.profiles.push({ id: randomUUID(), displayName, code, tokenHash: hash(token), createdAt: now(), friends: [], events: [] }));
        send(response, 201, { token, code, displayName }); return;
      }
      const paths = ['/v1/events', '/v1/friends', '/v1/leaderboard', '/v1/profile'];
      if (!paths.includes(url.pathname)) fail(404, 'not_found', 'Endpoint not found.');
      const profile = authenticate(request);
      if (request.method === 'POST' && url.pathname === '/v1/events') {
        const body = await readBody(request); exactObject(body, ['events']);
        if (!Array.isArray(body.events) || body.events.length > 500) fail(400, 'invalid_events', 'Send an events array of at most 500 items.');
        const time = now();
        for (const event of body.events) {
          exactObject(event, ['id', 'at']);
          if (typeof event.id !== 'string' || !/^[A-Za-z0-9][A-Za-z0-9:_-]{0,127}$/.test(event.id)
              || !Number.isSafeInteger(event.at) || event.at < 0 || event.at > time + 300_000) {
            fail(400, 'invalid_event', 'Events need a stable ID and a valid epoch-millisecond timestamp, at most five minutes ahead.');
          }
        }
        const result = store.change(data => {
          const current = data.profiles.find(p => p.id === profile.id);
          if (!current) fail(401, 'unauthorized', 'The profile no longer exists.');
          const ids = new Set(current.events.map(event => event.id));
          const days = new Map();
          for (const event of current.events) { const day = utcDay(event.at); days.set(day, (days.get(day) || 0) + 1); }
          let accepted = 0, duplicates = 0;
          for (const event of body.events) {
            if (ids.has(event.id)) { duplicates++; continue; }
            const day = utcDay(event.at);
            if ((days.get(day) || 0) >= 16) fail(422, 'daily_limit', 'The demo accepts at most 16 completed quests per UTC day.');
            current.events.push({ id: event.id, at: event.at }); ids.add(event.id); days.set(day, (days.get(day) || 0) + 1); accepted++;
          }
          if (current.events.length > MAX_PROFILE_EVENTS || data.profiles.reduce((count, p) => count + p.events.length, 0) > MAX_EVENTS) {
            fail(507, 'capacity', 'This demo server has reached its completion limit.');
          }
          return { accepted, duplicates, totalCompleted: current.events.length };
        });
        send(response, 200, result); return;
      }
      if (request.method === 'POST' && url.pathname === '/v1/friends') {
        limit(`friends:${profile.id}`, 10, 60_000);
        const body = await readBody(request); exactObject(body, ['code']); const code = codeValue(body.code);
        const friend = store.data.profiles.find(p => p.code === code);
        if (!friend) fail(404, 'friend_not_found', 'This friendship code was not found.');
        if (friend.id === profile.id) fail(400, 'own_code', 'Use a code shared by a friend.');
        const result = store.change(data => {
          const current = data.profiles.find(p => p.id === profile.id);
          const other = data.profiles.find(p => p.id === friend.id);
          if (!current) fail(401, 'unauthorized', 'The profile no longer exists.');
          if (!other) fail(404, 'friend_not_found', 'This friendship code was not found.');
          const added = !current.friends.includes(other.id);
          if (added && (current.friends.length >= MAX_FRIENDS || other.friends.length >= MAX_FRIENDS)) fail(422, 'friend_limit', 'A profile can have at most 100 friends in this demo.');
          if (added) { current.friends.push(other.id); other.friends.push(current.id); }
          return { friend: { id: other.id, displayName: other.displayName }, added };
        });
        send(response, 200, result); return;
      }
      if (request.method === 'GET' && url.pathname === '/v1/leaderboard') {
        const period = url.searchParams.get('period') || 'week';
        if (!['week', 'all'].includes(period) || [...url.searchParams.keys()].some(key => key !== 'period')) fail(400, 'invalid_period', 'Choose period=week or period=all.');
        const time = now(), start = period === 'week' ? time - 7 * DAY : 0;
        const allowed = new Set([profile.id, ...profile.friends]);
        const people = store.data.profiles.filter(p => allowed.has(p.id)).map(p => ({
          id: p.id, displayName: p.displayName,
          completed: p.events.filter(event => event.at >= start && event.at <= time).length,
          isYou: p.id === profile.id
        })).sort((a, b) => b.completed - a.completed || a.displayName.localeCompare(b.displayName) || a.id.localeCompare(b.id));
        let rank = 0, previous = null;
        people.forEach((person, index) => { if (person.completed !== previous) rank = index + 1; person.rank = rank; previous = person.completed; });
        send(response, 200, { period, windowStart: period === 'week' ? new Date(start).toISOString() : null, people, selfReported: true }); return;
      }
      if (request.method === 'DELETE' && url.pathname === '/v1/profile') {
        store.change(data => {
          data.profiles = data.profiles.filter(p => p.id !== profile.id);
          for (const person of data.profiles) person.friends = person.friends.filter(id => id !== profile.id);
        });
        send(response, 200, { deleted: true }); return;
      }
      fail(405, 'method_not_allowed', 'This endpoint does not support that method.');
    } catch (error) {
      const known = error instanceof ApiError;
      // Never log tokens, names, request bodies, friendship codes, or stored entries.
      if (!known) console.error('Tiny Wonder request failed:', error.code || error.name || 'internal error');
      send(response, known ? error.status : 500, { error: known ? error.code : 'internal_error', message: known ? error.message : 'The server could not save this request. Try again.' });
    }
  });
  server.requestTimeout = 15_000;
  server.headersTimeout = 10_000;
  server.keepAliveTimeout = 5_000;
  server.maxHeadersCount = 32;
  return server;
}

if (process.argv[1] && pathToFileURL(path.resolve(process.argv[1])).href === import.meta.url) {
  const host = process.env.HOST || '127.0.0.1';
  const port = Number(process.env.PORT || 8787);
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('PORT must be an integer from 1 to 65535.');
  const server = createServer();
  server.listen(port, host, () => console.log(`Tiny Wonder demo API listening on http://${host}:${port}`));
  for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => server.close(() => process.exit(0)));
}
