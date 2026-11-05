// RNF02: 50 req/s no Consolidado, com no máximo 5% de perda.
// Uso: k6 run load-tests/consolidado-pico.js
import http from 'k6/http';
import { check } from 'k6';

const LANCAMENTOS = __ENV.LANCAMENTOS_URL || 'http://localhost:8081';
const CONSOLIDADO = __ENV.CONSOLIDADO_URL || 'http://localhost:8082';

export const options = {
  scenarios: {
    pico: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.TAXA || 50),
      timeUnit: '1s',
      duration: __ENV.DURACAO || '10m',
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
  },
  thresholds: {
    http_req_failed: ['rate<=0.05'],
    http_req_duration: ['p(95)<200'],
    dropped_iterations: ['count==0'],
  },
};

export function setup() {
  const resposta = http.post(`${LANCAMENTOS}/dev/token`);
  return { token: resposta.json('access_token'), hoje: new Date().toISOString().slice(0, 10) };
}

export default function (dados) {
  const resposta = http.get(`${CONSOLIDADO}/consolidado/${dados.hoje}`, {
    headers: { Authorization: `Bearer ${dados.token}` },
    timeout: '2s',
  });
  check(resposta, { 'status 200': (r) => r.status === 200 });
}
