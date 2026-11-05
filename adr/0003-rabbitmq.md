# ADR-0003: RabbitMQ como broker

**Status:** Aceita

## Contexto
O sistema precisa de entrega durável, retry e DLQ, com baixa complexidade e execução local simples. Não há, por enquanto, necessidade de replay longo nem de muitos consumidores independentes.

## Decisão
Usar RabbitMQ com exchange do tipo topic, quorum queue, publisher confirms, ack manual e DLQ. Na AWS, a opção é o Amazon MQ for RabbitMQ.

## Alternativas
| Opção | Motivo para não escolher |
|---|---|
| Kafka | Excelente para alto volume e replay, mas superdimensionado neste caso |
| SQS/SNS | Muito simples de operar, mas prende ao provedor e exige emulação local |
| Redis Streams | Reaproveita o Redis, mas mistura cache com um dado que exige durabilidade |

## Consequências
- Operação e execução local ficam simples.
- Não há replay nativo; a reconstrução usa os lançamentos, que são a fonte da verdade.
- Migrar para Kafka é possível se surgirem novos consumidores ou necessidade de replay, pois o contrato do evento não muda.
