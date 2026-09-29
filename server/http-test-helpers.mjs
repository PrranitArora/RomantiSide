/** Bind an ephemeral test listener that Node's Fetch implementation permits. */
export async function listenForFetch(server, { probe = globalThis.fetch, attempts = 20 } = {}) {
  let rejectedPort;
  for (let attempt = 0; attempt < attempts; attempt++) {
    await new Promise((resolve, reject) => {
      const failed = error => reject(error);
      server.once('error', failed);
      server.listen(0, '127.0.0.1', () => { server.removeListener('error', failed); resolve(); });
    });
    const base = `http://127.0.0.1:${server.address().port}`;
    try {
      // Some OS ephemeral ranges include ports on Fetch's forbidden-port list.
      // Probe the actual runtime instead of maintaining a second copy of that list.
      const response = await probe(`${base}/health`);
      await response.arrayBuffer();
      if (!response.ok) throw new Error('The test server health check failed.');
      return base;
    } catch (error) {
      await new Promise((resolve, reject) => server.close(closeError => closeError ? reject(closeError) : resolve()));
      if (error?.cause?.message !== 'bad port') throw error;
      rejectedPort = error;
    }
  }
  throw new Error('Could not allocate a Fetch-compatible test port.', { cause: rejectedPort });
}
