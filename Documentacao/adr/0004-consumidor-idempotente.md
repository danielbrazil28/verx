# ADR-0004: Consumidor idempotente e somas comutativas

**Status:** Aceita

## Contexto
O broker pode entregar eventos duplicados ou fora de ordem, sobretudo com retries e múltiplos consumidores. Duplicar um evento financeiro distorce o saldo.

## Decisão
Em uma única transação:
1. Inserir o `eventId` em `eventos_processados`; se já existir, confirmar a mensagem e encerrar.
2. Fazer um upsert somando o valor em `consolidado_diario`, chave `(comerciante_id, data)`.
3. Confirmar a mensagem somente após o commit.

Como somas são comutativas, a ordem de chegada não muda o resultado final. Por isso, não é necessário garantir ordenação no broker.

Erros transitórios têm retry com backoff; após esgotar as tentativas, ou diante de evento inválido, a mensagem vai para a DLQ e gera alerta.

## Consequências
- O saldo converge mesmo com duplicidade e reordenação.
- Os consumidores podem escalar sem coordenação de ordem.
- `eventos_processados` cresce e precisa de retenção maior que a janela máxima de reentrega.
- Durante a convergência, a leitura pode refletir só parte dos eventos.
