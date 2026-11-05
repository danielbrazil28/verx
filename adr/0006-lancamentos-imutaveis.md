# ADR-0006: Lançamentos imutáveis e idempotência na API

**Status:** Aceita

## Contexto
Registros financeiros precisam de trilha de auditoria. Além disso, após um timeout, o cliente não sabe se o lançamento foi gravado e tende a repetir a requisição.

## Decisão
- Não permitir edição nem exclusão; correções são feitas por estorno.
- Exigir o cabeçalho `Idempotency-Key` em `POST /lancamentos`, com restrição única em `(comerciante_id, chave)` e hash do conteúdo.
- A mesma chave com o mesmo conteúdo retorna o resultado original; com conteúdo diferente, retorna `409`.
- Armazenar valores em centavos, como `BIGINT`.

## Alternativas
| Opção | Motivo para não escolher |
|---|---|
| Edição com tabela de histórico | Mais complexa e exige propagar diferenças ao Consolidado |
| Exclusão lógica | Não representa bem o fato de negócio |
| Deduplicação por valor e horário | Pode rejeitar duas vendas legítimas de mesmo valor |
| Event sourcing completo | Poderoso, mas excessivo; a tabela imutável já oferece a auditoria necessária |

## Consequências
- O histórico fica completo, e o consumidor só precisa somar eventos.
- Clientes precisam gerar e reutilizar a chave de idempotência.
- O estorno fica na data em que ocorreu, sem reescrever um dia já consultado.
