// Medicao limitada de GETs publicos em H2 local. Nao executa escrita nem login.
const assert = require('node:assert/strict');
const { performance } = require('node:perf_hooks');

const base = process.env.DELIBERA_BASE_URL || 'http://127.0.0.1:18441';
assert.ok(['127.0.0.1', 'localhost'].includes(new URL(base).hostname),
  'A medicao de carga so pode rodar contra localhost.');

const rounds = [
  { route: '/', count: 200, concurrency: 5 },
  { route: '/', count: 400, concurrency: 20 },
  { route: '/nova-solicitacao', count: 200, concurrency: 10 },
  { route: '/suporte', count: 200, concurrency: 10 },
];

async function measure({ route, count, concurrency }) {
  let next = 0;
  const samples = [];
  const codes = new Map();
  const begin = performance.now();
  async function worker() {
    while (next < count) {
      next++;
      const start = performance.now();
      try {
        const response = await fetch(base + route, { signal: AbortSignal.timeout(10000) });
        await response.arrayBuffer();
        codes.set(response.status, (codes.get(response.status) || 0) + 1);
      } catch (error) {
        codes.set(error.name, (codes.get(error.name) || 0) + 1);
      }
      samples.push(performance.now() - start);
    }
  }
  await Promise.all(Array.from({ length: concurrency }, worker));
  samples.sort((a, b) => a - b);
  const percentile = p => Math.round(samples[Math.min(samples.length - 1, Math.ceil(samples.length * p) - 1)]);
  const seconds = (performance.now() - begin) / 1000;
  return { route, count, concurrency, seconds: +seconds.toFixed(2),
    rps: +(count / seconds).toFixed(1), p50ms: percentile(.50),
    p95ms: percentile(.95), p99ms: percentile(.99), codes: Object.fromEntries(codes) };
}

(async () => {
  for (const round of rounds) console.log(JSON.stringify(await measure(round)));
})().catch(error => { console.error(error); process.exitCode = 1; });
