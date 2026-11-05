# ADR-0007: Java 21, Spring Boot e PostgreSQL

**Status:** Aceita

## Contexto
O desafio permite qualquer linguagem. A escolha deve priorizar domínio da equipe, maturidade do ecossistema e transações confiáveis.

## Decisão
| Camada | Escolha | Justificativa |
|---|---|---|
| Linguagem | Java 21 (LTS) | Suporte longo e ecossistema corporativo maduro |
| Framework | Spring Boot | Integração com AMQP, segurança, validação e métricas |
| Banco | PostgreSQL, uma base por serviço | ACID, upsert nativo e `SKIP LOCKED` |
| Migração | Flyway | Esquema versionado |
| Testes | JUnit 5, Testcontainers e k6 | Integração com dependências reais e prova de carga |
| Observabilidade | OpenTelemetry, Prometheus e Grafana | Padrão aberto, sem dependência de fornecedor |
| Execução local | Docker Compose | Ambiente completo com um comando |

## Alternativas
| Opção | Motivo para não escolher |
|---|---|
| Node.js ou Python | Viáveis, mas sem vantagem para este problema |
| Go | Consumo menor de recursos, sem necessidade comprovada para esta carga |
| Banco NoSQL | A transação local entre lançamento e outbox é central, e o modelo relacional a atende diretamente |

## Consequências
- O consumo de memória da JVM é maior, com impacto de custo a avaliar na nuvem.
- O uso de virtual threads só deve ser adotado se os testes mostrarem benefício.
