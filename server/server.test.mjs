import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { createServer } from './server.mjs';

const NOW = Date.UTC(2026, 8, 29, 18);
const DAY = 86_400_000;

async function setup(t) {
  const directory = mkdtempSync(path.join(tmpdir(), 'tiny-wonder-api-test-'));
  const dataFile = path.join(directory, 'state.json');
  let server, base;
  async function open() {
    server = createServer({ dataFile, now: () => NOW });
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    base = `http://127.0.0.1:${server.address().port}`;
  }
  async function close() { await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve())); }
  await open();
  t.after(async () => {
    await close();
    // Only this freshly allocated test directory is removed.
    const resolved = path.resolve(directory);
    assert.equal(path.dirname(resolved), path.resolve(tmpdir()));
    assert.ok(path.basename(resolved).startsWith('tiny-wonder-api-test-'));
    rmSync(resolved, { recursive: true, force: true });
  });
  async function request(method, endpoint, token, body) {
    const response = await fetch(base + endpoint, {
      method,
      headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}) },
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    return { status: response.status, body: await response.json(), headers: response.headers };
  }
  async function profile(displayName) {
    const result = await request('POST', '/v1/profile', null, { displayName });
    assert.equal(result.status, 201);
    return result.body;
  }
  return { request, profile, dataFile, restart: async () => { await close(); await open(); } };
}

test('health exposes no profiles and all private endpoints require valid auth', async t => {
  const api = await setup(t);
  const health = await api.request('GET', '/health');
  assert.deepEqual(health.body, { ok: true, service: 'tiny-wonder-friends-demo' });
  assert.equal(health.headers.get('cache-control'), 'no-store');
  const alice = await api.profile('Alice');
  assert.match(alice.token, /^[A-Za-z0-9_-]{43}$/);
  assert.match(alice.code, /^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{12}$/);
  for (const [method, endpoint, body] of [['GET', '/v1/leaderboard'], ['POST', '/v1/events', { events: [] }], ['POST', '/v1/friends', { code: alice.code }], ['DELETE', '/v1/profile']]) {
    assert.equal((await api.request(method, endpoint, undefined, body)).status, 401);
    assert.equal((await api.request(method, endpoint, 'x'.repeat(43), body)).status, 401);
  }
  const stored = readFileSync(api.dataFile, 'utf8');
  assert.equal(stored.includes(alice.token), false, 'raw token must not be persisted');
  assert.match(JSON.parse(stored).profiles[0].tokenHash, /^[a-f0-9]{64}$/);
});

test('private mutual friendships, idempotent events, ranks, ties, and rolling windows', async t => {
  const api = await setup(t);
  const alice = await api.profile('Alice'), bob = await api.profile('Bob'), charlie = await api.profile('Charlie');
  const aEvents = [{ id: 'light:2026-9-29', at: NOW - 1000 }, { id: 'good:2026-9-29', at: NOW - 2000 }, { id: 'light:2020-1-1', at: Date.UTC(2020, 0, 1) }];
  assert.deepEqual((await api.request('POST', '/v1/events', alice.token, { events: aEvents })).body, { accepted: 3, duplicates: 0, totalCompleted: 3 });
  assert.deepEqual((await api.request('POST', '/v1/events', alice.token, { events: aEvents })).body, { accepted: 0, duplicates: 3, totalCompleted: 3 });
  await api.request('POST', '/v1/events', bob.token, { events: [{ id: 'strength:today', at: NOW - 1000 }, { id: 'light:today', at: NOW - 2000 }] });
  const alone = await api.request('GET', '/v1/leaderboard?period=week', alice.token);
  assert.deepEqual(alone.body.people.map(p => p.displayName), ['Alice']);
  const added = await api.request('POST', '/v1/friends', alice.token, { code: bob.code.toLowerCase() });
  assert.equal(added.body.added, true);
  assert.equal(added.body.friend.displayName, 'Bob');
  assert.equal((await api.request('POST', '/v1/friends', alice.token, { code: bob.code })).body.added, false);
  const board = (await api.request('GET', '/v1/leaderboard?period=week', alice.token)).body;
  assert.equal(board.windowStart, new Date(NOW - 7 * DAY).toISOString());
  assert.equal(board.selfReported, true);
  assert.deepEqual(board.people.map(p => [p.displayName, p.completed, p.rank, p.isYou]), [['Alice', 2, 1, true], ['Bob', 2, 1, false]]);
  const bobBoard = (await api.request('GET', '/v1/leaderboard?period=all', bob.token)).body;
  assert.equal(bobBoard.windowStart, null);
  assert.deepEqual(bobBoard.people.map(p => [p.displayName, p.completed, p.rank]), [['Alice', 3, 1], ['Bob', 2, 2]]);
  assert.equal(bobBoard.people.find(p => p.displayName === 'Bob').isYou, true);
  await api.request('POST', '/v1/friends', bob.token, { code: charlie.code });
  const privateBoard = (await api.request('GET', '/v1/leaderboard', charlie.token)).body;
  assert.deepEqual(privateBoard.people.map(p => p.displayName).sort(), ['Bob', 'Charlie']);
  assert.equal(JSON.stringify(privateBoard).includes(alice.code), false);
  assert.equal(JSON.stringify(privateBoard).includes('tokenHash'), false);
  assert.deepEqual(Object.keys(privateBoard.people[0]).sort(), ['completed', 'displayName', 'id', 'isYou', 'rank']);
});

test('durable restart preserves auth and scores; deletion removes relationships and credentials', async t => {
  const api = await setup(t);
  const alice = await api.profile('Alice'), bob = await api.profile('Bob');
  await api.request('POST', '/v1/friends', alice.token, { code: bob.code });
  await api.request('POST', '/v1/events', bob.token, { events: [{ id: 'thanks:today', at: NOW - 500 }] });
  await api.restart();
  assert.equal((await api.request('GET', '/v1/leaderboard', alice.token)).body.people.length, 2);
  assert.deepEqual((await api.request('DELETE', '/v1/profile', bob.token)).body, { deleted: true });
  assert.equal((await api.request('GET', '/v1/leaderboard', bob.token)).status, 401);
  assert.equal((await api.request('POST', '/v1/friends', alice.token, { code: bob.code })).status, 404);
  const board = (await api.request('GET', '/v1/leaderboard', alice.token)).body;
  assert.deepEqual(board.people.map(p => p.displayName), ['Alice']);
  const stored = JSON.parse(readFileSync(api.dataFile, 'utf8'));
  assert.equal(stored.profiles.length, 1);
  assert.deepEqual(stored.profiles[0].friends, []);
  assert.equal(JSON.stringify(stored).includes('thanks:today'), false);
});

test('rejects malformed input, future events, extra sensitive fields, and seventeenth UTC-daily event atomically', async t => {
  const api = await setup(t);
  for (const displayName of ['', 'x'.repeat(33), 'name\u0000', 'name\u202e']) {
    assert.equal((await api.request('POST', '/v1/profile', null, { displayName })).status, 400);
  }
  assert.equal((await api.request('POST', '/v1/profile', null, { displayName: 'Name', contacts: [] })).status, 400);
  const alice = await api.profile(' Aki 🌸 ');
  assert.equal(alice.displayName, 'Aki 🌸');
  for (const events of [[{ id: 'future', at: NOW + 300_001 }], [{ id: 'bad', at: -1 }], [{ id: 'bad', at: 'today' }], [{ id: 'bad', at: NOW, mood: 1 }], [{ id: '<script>', at: NOW }], Array.from({ length: 501 }, (_, i) => ({ id: `q${i}`, at: NOW }))]) {
    assert.equal((await api.request('POST', '/v1/events', alice.token, { events })).status, 400);
  }
  assert.equal((await api.request('GET', '/v1/leaderboard?period=forever', alice.token)).status, 400);
  assert.equal((await api.request('POST', '/v1/friends', alice.token, { code: alice.code })).status, 400);
  assert.equal((await api.request('POST', '/v1/friends', alice.token, { code: '123' })).status, 400);
  const sixteen = Array.from({ length: 16 }, (_, i) => ({ id: `quest${i}:today`, at: NOW - 100 }));
  assert.equal((await api.request('POST', '/v1/events', alice.token, { events: [...sixteen, { id: 'seventeenth', at: NOW - 100 }] })).status, 422);
  assert.equal((await api.request('GET', '/v1/leaderboard', alice.token)).body.people[0].completed, 0);
  assert.equal((await api.request('POST', '/v1/events', alice.token, { events: sixteen })).body.accepted, 16);
  assert.equal((await api.request('POST', '/v1/events', alice.token, { events: [{ id: 'seventeenth', at: NOW - 100 }] })).status, 422);
  assert.equal((await api.request('POST', '/v1/events', alice.token, { events: sixteen })).body.duplicates, 16);
  assert.equal((await api.request('POST', '/v1/events', alice.token, { events: [], notes: 'private' })).status, 400);
});

test('registration rate limit is enforced without exposing profile data', async t => {
  const api = await setup(t);
  for (let i = 0; i < 10; i++) await api.profile(`Demo ${i}`);
  const result = await api.request('POST', '/v1/profile', null, { displayName: 'One too many' });
  assert.equal(result.status, 429);
  assert.equal(result.body.error, 'rate_limit');
  assert.equal(result.headers.get('retry-after'), '60');
});
