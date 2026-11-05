// RNF01: registra lançamentos enquanto o Consolidado é derrubado.
// Uso: k6 run load-tests/lancamentos-isolamento.js
// Durante a execução: docker compose stop consolidado
import http from 'k6/http';
import { check } from 'k6';

const LANCAMENTOS = __ENV.LANCAMENTOS_URL || 'http://localhost:8081';

export const options = {
  scenarios: {
    registros: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.TAXA || 20),
      timeUnit: '1s',
      duration: __ENV.DURACAO || '2m',
      preAllocatedVUs: 20,
      maxVUs: 100,
    },
  },
  thresholds: { http_req_failed: ['rate==0'] },
};

export function setup() {
  return { token: http.post(`${LANCAMENTOS}/dev/token`).json('access_token') };
}

export default function (dados) {
  const resposta = http.post(
    `${LANCAMENTOS}/lancamentos`,
    JSON.stringify({ tipo: 'CREDITO', valorCentavos: 100 }),
    {
      headers: {
        Authorization: `Bearer ${dados.token}`,
        'Content-Type': 'application/json',
        'Idempotency-Key': `${__VU}-${__ITER}-${Date.now()}`,
      },
    },
  );
  check(resposta, { 'status 201': (r) => r.status === 201 });
}
