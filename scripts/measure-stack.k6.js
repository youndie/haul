// B-173's load: anonymous GETs of the storefront's main trees at a constant arrival rate, open-loop, so a slow
// answer does not slow the offer (a closed loop never takes the slow samples). One console line per request —
// `round,route,status,client_ms,server_ms` — which `scripts/measure-stack.py` reads; the summary it exports
// says how many arrivals were made and how many k6 could not start (`dropped_iterations`).
//
//   k6 run -e ORIGIN=http://127.0.0.1:18480 -e RATE=100 -e SECONDS=60 -e ROUND=3 -e ROUTES=mix \
//     -e SUMMARY=/tmp/summary.json --console-output=/tmp/requests.txt scripts/measure-stack.k6.js
import http from 'k6/http';
import exec from 'k6/execution';

const ORIGIN = __ENV.ORIGIN;
const RATE = parseInt(__ENV.RATE, 10);
const SECONDS = parseInt(__ENV.SECONDS, 10);
const ROUND = __ENV.ROUND;

// The prefixes `scripts/suggest-latency.sh` walks: a shopper typing B-09's queries, from two characters.
const QUERIES = ['running shoes', 'headphones', 'sonora', 'wireless earbuds', 'yoga mats', 'heaphones', 'xqzt'];
const PREFIXES = QUERIES.flatMap((q) => [...Array(q.length - 1).keys()].map((n) => q.slice(0, n + 2)));

// The five routes of the mix, in equal shares in turn; `healthz` is the readers' known-order control alone.
const MIX = [
  ['home', () => '/ui/home'],
  ['category', () => '/ui/c/electronics/audio/headphones'],
  ['product', () => '/ui/p/p-sonora-sh-718n'],
  ['suggest', (i) => '/ui/search/suggest?q=' + encodeURIComponent(PREFIXES[i % PREFIXES.length])],
  ['live', () => '/ui/live'],
];
const ROUTES = __ENV.ROUTES === 'healthz' ? [['healthz', () => '/healthz']] : MIX;

export const options = {
  discardResponseBodies: true,
  summaryTrendStats: ['med', 'p(99)', 'max'],
  scenarios: {
    gets: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: `${SECONDS}s`,
      preAllocatedVUs: Math.min(400, Math.max(20, RATE)),
      maxVUs: 600,
    },
  },
};

export default function () {
  const i = exec.scenario.iterationInTest;
  const [name, path] = ROUTES[i % ROUTES.length];
  const res = http.get(ORIGIN + path(Math.floor(i / ROUTES.length)), { timeout: '10s', tags: { route: name } });
  const timing = res.headers['Server-Timing'] || '';
  const match = /dur=([0-9.]+)/.exec(timing);
  console.log(`${ROUND},${name},${res.status},${res.timings.duration.toFixed(2)},${match ? match[1] : ''}`);
}

export function handleSummary(data) {
  const count = (name) => (data.metrics[name] ? data.metrics[name].values.count : 0);
  return {
    [__ENV.SUMMARY]: JSON.stringify({
      round: ROUND,
      rate: RATE,
      seconds: SECONDS,
      iterations: count('iterations'),
      dropped: count('dropped_iterations'),
      failed: data.metrics.http_req_failed ? data.metrics.http_req_failed.values.passes : 0,
    }),
  };
}
