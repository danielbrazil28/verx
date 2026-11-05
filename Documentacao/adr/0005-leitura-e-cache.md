# ADR-0005: Modelo de leitura com cache Redis

**Status:** Aceita

## Contexto
O Consolidado precisa suportar 50 req/s com até 5% de perda. Recalcular a partir de lançamentos a cada consulta seria custoso e dependeria de outro serviço. Além disso, um lançamento retroativo altera o saldo acumulado de todos os dias seguintes (RN10).

## Decisão
- Persistir apenas totais diários de crédito e débito.
- Calcular o saldo acumulado na consulta, somando os dias até a data pedida. Assim, um lançamento retroativo atualiza uma única linha, sem cascata.
- Usar cache-aside no Redis, com TTL de 5 minutos e chave `consolidado:{comerciante}:{versao}:{data}`.
- Incrementar a versão do comerciante ao aplicar um evento, invalidando todas as datas dele de uma vez.
- Se o Redis falhar, consultar o Postgres.

## Alternativas
| Opção | Motivo para não escolher |
|---|---|
| Sem cache | 50 req/s é viável com índice adequado, mas com menos folga em picos; o teste k6 deve medir essa diferença |
| Saldo acumulado pré-calculado | Leitura direta, mas lançamentos retroativos exigem atualização em cascata |
| Só TTL curto | Mais simples, mas pode servir saldo desatualizado e reduz o acerto de cache |

## Consequências
- A consulta não depende de Lançamentos nem do broker.
- A falha do Redis degrada a latência, sem indisponibilizar o serviço.
- Em dias de muitos lançamentos, a invalidação frequente reduz o acerto de cache. Se as medições mostrarem impacto, a evolução é agregar invalidações em janelas curtas.
