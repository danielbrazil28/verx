# ADR-0001: Dois serviços independentes

**Status:** Aceita

## Contexto
Lançamentos não pode ficar indisponível se o Consolidado cair. Os dois também têm perfis de carga diferentes: um prioriza escrita durável; o outro, leitura em pico.

## Decisão
Criar dois serviços implantáveis separadamente, cada um com seu banco. Eles só se comunicam por eventos.

## Alternativas
| Opção | Motivo para não escolher |
|---|---|
| Monolito | Mais simples, mas compartilha processo e recursos; uma falha ou pico no relatório pode afetar o registro |
| Monolito modular com workers | Boa opção de baixo custo, mas com isolamento e escala independente mais fracos |
| Muitos microsserviços | Complexidade sem ganho: há apenas dois contextos de negócio |
| Serverless | Viável, mas dificulta a execução local e cria maior dependência do provedor |

## Consequências
- Falhas e escala ficam isoladas.
- Há custo operacional de dois deploys, além de broker e consistência eventual.
- Mais serviços só devem surgir quando houver novo contexto de negócio.
