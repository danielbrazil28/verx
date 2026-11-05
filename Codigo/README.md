# Fluxo de Caixa Diário

Dois serviços para um comerciante controlar o fluxo de caixa: **Lançamentos** registra débitos e créditos, e **Consolidado** informa o saldo diário. Eles só se comunicam por eventos, então o registro continua funcionando se o Consolidado cair.

```
Cliente ──> Lançamentos ──> PostgreSQL (lançamento + outbox, mesma transação)
                                 │ publicador do outbox
                                 v
                             RabbitMQ ──> Consumidor idempotente ──> PostgreSQL (totais diários)
                                                                          ^
Cliente ──> Consolidado ──> Redis (cache) ── se falhar ───────────────────┘
```

## Documentação

- [Domínios e capacidades](docs/01-dominios-e-capacidades.md)
- [Requisitos refinados](docs/02-requisitos.md)
- [Decisões arquiteturais (ADRs)](docs/adr/README.md)
- `docs/Fluxo_de_Caixa_Arquitetura.docx`: documento completo, com arquitetura alvo e de transição, custos e observabilidade

## Como rodar localmente

Pré-requisito: Docker Desktop. A compilação acontece dentro dos containers.

```bash
docker compose up -d --build
```

| Serviço | Endereço |
|---|---|
| Lançamentos | http://localhost:8081 |
| Consolidado | http://localhost:8082 |
| RabbitMQ (guest/guest) | http://localhost:15672 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 |

## Como usar

Os exemplos usam bash. No Windows, rode-os no Git Bash.

```bash
# Token de desenvolvimento, disponível apenas no perfil "local"
TOKEN=$(curl -s -X POST http://localhost:8081/dev/token | sed 's/.*"access_token":"\([^"]*\)".*/\1/')

# Registrar crédito de R$ 150,00
curl -i -X POST http://localhost:8081/lancamentos \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -H "Idempotency-Key: venda-001" \
  -d '{"tipo":"CREDITO","valorCentavos":15000}'

# Consultar o saldo de hoje
curl -H "Authorization: Bearer $TOKEN" http://localhost:8082/consolidado/$(date +%F)
```

| Endpoint | Descrição |
|---|---|
| `POST /lancamentos` | Registra um lançamento; exige `Idempotency-Key` |
| `POST /lancamentos/{id}/estorno` | Estorna um lançamento; exige `Idempotency-Key` |
| `GET /lancamentos/{id}` | Consulta um lançamento |
| `GET /lancamentos?de=&ate=&pagina=&tamanho=` | Lista por período |
| `GET /consolidado/{data}` | Créditos, débitos, saldo do dia e acumulado |
| `GET /consolidado?de=&ate=` | Dias com movimento no período |

Valores são sempre em centavos. A data de competência é opcional e, se omitida, vale o dia atual no fuso do comerciante.

## Testes

Exigem Java 21, Maven e Docker em execução, pois os testes de integração usam Testcontainers.

```bash
mvn test     # unitários
mvn verify   # unitários e integração
```

Testes de carga, com [k6](https://k6.io):

```bash
k6 run load-tests/consolidado-pico.js        # RNF02: 50 req/s, até 5% de perda
k6 run load-tests/lancamentos-isolamento.js  # RNF01: rode "docker compose stop consolidado" durante o teste
```

### Resultados medidos

| Cenário | Critério | Resultado |
|---|---|---|
| 50 req/s no Consolidado por 10 min | Erros ≤ 5%; p95 < 200 ms | A medir |
| Registros com o Consolidado fora do ar | Nenhuma falha | A medir |

## Simplificações em relação à arquitetura alvo

- **Identidade:** localmente, os tokens são emitidos por `/dev/token` com chave HS256. Para usar um provedor OIDC, como o Keycloak, basta definir `JWT_ISSUER_URI`.
- **Observabilidade:** há métricas no Prometheus, incluindo `outbox_pendentes` e `consolidado_atraso`. Traces distribuídos, painéis e alertas ainda não foram implementados.
- **Alta disponibilidade:** o Compose roda uma instância de cada componente.
- **Reconstrução do consolidado (RF07):** ainda não foi implementada.
