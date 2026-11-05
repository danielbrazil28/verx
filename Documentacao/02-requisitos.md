# 2. Requisitos refinados

## Premissas a validar com o negócio
| # | Premissa | Se estiver errada |
|---|---|---|
| P1 | "50 req/s no consolidado" se refere a consultas | O teste de ingestão de eventos já cobre a outra leitura; revisar o dimensionamento do consumidor |
| P2 | "Máximo de 5% de perda" significa pelo menos 95% de respostas bem-sucedidas no pico, ou seja, 47,5 req/s | Ajustar as metas e o teste de carga |
| P3 | Atraso de segundos no consolidado é aceitável | Rever a consolidação assíncrona (ADR-0002) |
| P4 | Lançamentos retroativos são permitidos; datas futuras, não | Ajustar RN04 |
| P5 | Moeda única (BRL) e um fuso por comerciante | Incluir moeda no modelo e na chave do consolidado |
| P6 | Saldo negativo é permitido, pois o sistema registra fatos e não autoriza pagamentos | Criar regra de validação |

## Requisitos funcionais
| ID | Requisito | Serviço |
|---|---|---|
| RF01 | Registrar lançamento de crédito ou débito | Lançamentos |
| RF02 | Estornar lançamento existente | Lançamentos |
| RF03 | Consultar um lançamento e listar por período, com paginação | Lançamentos |
| RF04 | Atualizar o consolidado a partir de eventos | Consolidado |
| RF05 | Consultar créditos, débitos, saldo do dia e saldo acumulado por data | Consolidado |
| RF06 | Consultar o consolidado por período | Consolidado |
| RF07 | Reconstruir o consolidado a partir dos lançamentos (evolução) | Consolidado |

## Regras de negócio
| ID | Regra |
|---|---|
| RN01 | O valor é sempre positivo; o sentido vem do tipo (`CREDITO` ou `DEBITO`) |
| RN02 | Valores são armazenados em centavos inteiros, nunca em ponto flutuante |
| RN03 | Lançamentos são imutáveis: não há edição nem exclusão |
| RN04 | A data de competência não pode estar no futuro, considerando o fuso do comerciante |
| RN05 | Correções são feitas por estorno, com tipo inverso, vínculo ao lançamento original e data de competência igual à do estorno |
| RN06 | Um lançamento pode ser estornado uma única vez, e um estorno não pode ser estornado |
| RN07 | Repetir uma requisição com a mesma `Idempotency-Key` retorna o resultado original, sem novo lançamento |
| RN08 | Reutilizar a chave com conteúdo diferente retorna `409 Conflict` |
| RN09 | Um comerciante só acessa os próprios dados; o identificador vem do token, nunca do corpo da requisição |
| RN10 | Um lançamento retroativo altera o dia de competência e o saldo acumulado dos dias seguintes |

## Requisitos não funcionais
As metas abaixo são propostas de projeto. Somente RNF01 e RNF02 vêm diretamente do desafio. Os valores medidos devem ser publicados no README após os testes.

| ID | Atributo | Meta | Como validar |
|---|---|---|---|
| RNF01 | Isolamento de falhas | Lançamentos continua operando com o Consolidado fora do ar | Derrubar o Consolidado durante o teste de carga |
| RNF02 | Vazão e perda | 50 req/s no Consolidado com até 5% de erro | k6 com taxa de chegada constante |
| RNF03 | Latência de consulta | p95 < 200 ms no pico | k6 |
| RNF04 | Latência de registro | p95 < 300 ms | k6 |
| RNF05 | Atualização do consolidado | p95 < 5 s em operação normal | Métrica entre `occurredAt` e a aplicação do evento |
| RNF06 | Durabilidade | Nenhum lançamento confirmado é perdido | Reiniciar RabbitMQ e Consolidado e comparar totais |
| RNF07 | Consistência final | Consolidado igual à soma dos lançamentos após esvaziar a fila | Teste de reconciliação |
| RNF08 | Disponibilidade (alvo em nuvem) | Lançamentos 99,9%; Consolidado 99,5% | SLOs e alertas |
| RNF09 | Recuperação (alvo em nuvem) | RPO ≤ 5 min; RTO ≤ 30 min | Exercício de restauração |
| RNF10 | Escalabilidade | Serviços sem estado, escaláveis horizontalmente | Teste com mais de uma réplica |
| RNF11 | Segurança | JWT, TLS, isolamento por comerciante e auditoria | Testes de autorização e varredura de dependências |
| RNF12 | Observabilidade | Trace ponta a ponta, do HTTP ao consumidor | Verificar no Grafana |

## Cenários de falha
| Falha | Comportamento esperado |
|---|---|
| Consolidado fora do ar | Lançamentos aceita registros; eventos aguardam na fila |
| RabbitMQ fora do ar | Lançamentos aceita registros; eventos aguardam no outbox |
| Redis fora do ar | Consultas vão ao Postgres, com maior latência |
| Banco de Lançamentos fora do ar | Registro retorna `503`; o cliente repete com a mesma chave |
| Evento duplicado | Ignorado pelo consumidor |
| Eventos fora de ordem | Mesmo resultado final, pois as somas são comutativas |
| Evento inválido | Vai para a DLQ, com alerta |
