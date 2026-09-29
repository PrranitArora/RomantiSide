import test from 'node:test';
import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import { listenForFetch } from './http-test-helpers.mjs';

function fakeServer(ports) {
  const server = new EventEmitter();
  server.closed = 0;
  server.allocations = [];
  server.listen = (port, host, ready) => {
    server.allocations.push({ port, host });
    server.port = ports.shift();
    queueMicrotask(ready);
  };
  server.address = () => ({ port: server.port });
  server.close = ready => { server.closed++; queueMicrotask(ready); };
  return server;
}

test('test listener retries a Fetch-blocked ephemeral port without masking other errors', async () => {
  const server = fakeServer([6000, 49152]);
  const probed = [];
  const base = await listenForFetch(server, { probe: async url => {
    probed.push(url);
    if (url.includes(':6000/')) throw new TypeError('fetch failed', { cause: new Error('bad port') });
    return new Response('{}', { status: 200 });
  } });
  assert.equal(base, 'http://127.0.0.1:49152');
  assert.equal(server.closed, 1, 'the rejected listener must close before reallocating');
  assert.deepEqual(server.allocations, [{ port: 0, host: '127.0.0.1' }, { port: 0, host: '127.0.0.1' }]);
  assert.deepEqual(probed, ['http://127.0.0.1:6000/health', 'http://127.0.0.1:49152/health']);
  assert.equal(server.listenerCount('error'), 0);

  const broken = fakeServer([49153]);
  const networkError = new TypeError('fetch failed', { cause: new Error('connection reset') });
  await assert.rejects(listenForFetch(broken, { probe: async () => { throw networkError; } }), error => error === networkError);
  assert.equal(broken.closed, 1);
  assert.equal(broken.allocations.length, 1, 'ordinary network failures must not be retried');
});

test('test listener bounds repeated forbidden-port allocations and closes each listener', async () => {
  const server = fakeServer([6000, 6000]);
  await assert.rejects(listenForFetch(server, {
    attempts: 2,
    probe: async () => { throw new TypeError('fetch failed', { cause: new Error('bad port') }); }
  }), /Could not allocate a Fetch-compatible test port/);
  assert.equal(server.allocations.length, 2);
  assert.equal(server.closed, 2);
});
