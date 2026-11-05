# ADR-0002: Consolidação assíncrona com Transactional Outbox

**Status:** Aceita

## Contexto
Uma chamada síncrona acoplaria a disponibilidade de Lançamentos à do Consolidado. Já gravar no banco e depois publicar no broker cria o problema de escrita dupla: uma falha entre as duas etapas perde o evento.

## Decisão
Gravar o lançamento e o evento na mesma transação local. Um publicador lê a tabela `outbox` e envia ao broker, com confirmação antes de marcar o evento como publicado. Com múltiplas réplicas, a leitura usa `FOR UPDATE SKIP LOCKED`.

## Alternativas
| Opção | Motivo para não escolher |
|---|---|
| HTTP síncrono com circuit breaker | Evita a queda em cascata, mas não entrega o evento sem um mecanismo adicional |
| Publicação direta no broker | Sujeita à escrita dupla, e o registro passa a depender do broker |
| CDC com Debezium | Robusto e sem polling, mas adiciona infraestrutura demais para este volume; é a evolução natural |
| Consulta direta ao banco de Lançamentos | Acopla os serviços ao esquema e à carga um do outro |

## Consequências
- Lançamentos continua operando com o Consolidado ou o broker fora do ar.
- A entrega é pelo menos uma vez, o que exige consumidor idempotente (ADR-0004).
- O consolidado tem consistência eventual; o atraso é monitorado (RNF05).
- A tabela `outbox` precisa de rotina de limpeza.
