# ADR-0008: Autenticação OAuth2/JWT e isolamento por comerciante

**Status:** Aceita

## Contexto
Dados financeiros de um comerciante não podem ser expostos a outro. Identidade é uma capacidade genérica e não deve ser construída do zero.

## Decisão
- Delegar a autenticação a um provedor OIDC no alvo (Keycloak ou serviço gerenciado). Localmente, para simplificar, os tokens vêm de um emissor de desenvolvimento com chave HS256, ativo só no perfil `local`.
- Validar assinatura, emissor, audiência e validade do JWT em cada serviço.
- Usar os escopos `lancamentos:write`, `lancamentos:read` e `consolidado:read`.
- Obter o `comercianteId` apenas do token e aplicá-lo em todas as consultas.
- Exigir TLS externamente e credenciais próprias para cada serviço no broker e no banco, com privilégio mínimo.
- Manter segredos fora do código; no alvo, usar um gerenciador de segredos.
- Não registrar tokens nem dados sensíveis em logs.
- Aplicar rate limit e WAF na borda, garantindo folga acima do pico de 50 req/s.

## Alternativas
| Opção | Motivo para não escolher |
|---|---|
| API key | Simples, mas sem expiração curta, escopos ou identidade padronizada |
| Autenticação própria | Maior risco e nenhum diferencial para o negócio |
| Validar só no gateway | Sem defesa em profundidade: um acesso interno contornaria o controle |

## Consequências
- O isolamento fica claro e pode ser testado.
- O provedor OIDC é um componente a mais no ambiente alvo.
- mTLS entre serviços fica como evolução, pois eles não fazem chamadas síncronas entre si.
