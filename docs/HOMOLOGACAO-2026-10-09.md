# Homologação local — 09/10/2026

Ambiente: Java 21, Spring Boot 3.4.1, perfil H2 descartável, email simulado e servidor vinculado a `127.0.0.1:18441`. Os testes não acessaram dados reais. A demonstração em GitHub Pages é estática e não equivale ao backend.

`mvnw test` terminou com **199 testes, zero falhas e zero erros**. `npm test --prefix e2e` e `npm run test:ajuda --prefix e2e` passaram em Chromium local após as correções.

## Matriz executada

| Área | Cenários verificados | Resultado |
| --- | --- | --- |
| Duas instituições | Coordenação da instituição A tenta ver e deferir solicitação da B; dashboard, CSV e download de anexo da B; controle com download de anexo próprio. | Bloqueio confirmado, dados alheios ausentes e estado preservado. |
| Perfis | Acesso de coordenação, admin institucional, gestão, ROOT e anônimo às rotas protegidas. | Permissões da matriz atual confirmadas. |
| Professor | Consulta por protocolo e código, resposta, reuso do código, resposta após decisão final e POST sem CSRF. | Resposta aceita uma vez; reuso e reabertura bloqueados. |
| Ajuda | FAQ, largura móvel, formulário HTMX, campos inválidos, CSRF e sexta mensagem do mesmo IP. | Navegador e backend passaram; cota agora exibe erro. |
| Aluno | Criação de solicitação fictícia, consulta por protocolo e email, menu móvel. | Playwright passou. |
| Segurança de anexos | Download próprio e tentativa de outro tenant; validação de tipos de arquivo já coberta pela suíte existente. | Download cruzado retorna 404. |

O teste integrado do professor revelou `LazyInitializationException` em `/parecer-professor` e `/parecer-professor/responder`, exibida como tela 500. A consulta do parecer passou a carregar solicitação e histórico em conjunto. Os testes do fluxo passaram após a correção. O suporte aceitava valores vazios enviados fora do navegador e mostrava fragmento de sucesso ao esgotar a cota; a validação no servidor e o fragmento de erro corrigiram ambos.

## Carga local de leitura

Roteiro reproduzível: `node e2e/carga-local.cjs`. Cada rodada usa GETs públicos, lê a resposta inteira e impõe timeout de 10 segundos. As rodadas foram sequenciais na instância H2 local, antes das correções do parecer e suporte, que não alteraram as páginas medidas. Não houve escrita nem tráfego contra o Fiplan.

| Rota | Requisições | Concorrência | Vazão (req/s) | p50 | p95 | p99 | HTTP 200 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `/` | 200 | 5 | 58,8 | 47 ms | 158 ms | 788 ms | 200 |
| `/` | 400 | 20 | 159,0 | 101 ms | 304 ms | 564 ms | 400 |
| `/nova-solicitacao` | 200 | 10 | 150,6 | 63 ms | 107 ms | 121 ms | 200 |
| `/suporte` | 200 | 10 | 224,4 | 36 ms | 90 ms | 122 ms | 200 |

São medidas de uma execução, com H2 e cache/recursos do PC. Não estimam capacidade de produção, gravações concorrentes, anexos, SMTP ou PostgreSQL. Antes do piloto, repetir em ambiente semelhante ao de produção com metas de latência, carga de escrita e observabilidade.

## Pendências de homologação

1. Fluxo autenticado completo de gestão e coordenação no navegador, com contas de duas instituições e anexos reais.
2. Concorrência em decisões e respostas do professor; avaliar atualização otimista em `Solicitacao` e consumo atômico do código.
3. Carga de escrita, uploads e banco PostgreSQL; definir metas e executar ensaio de duração maior.
4. Teste de segurança externo com HTTPS, cookies, sessão, cabeçalhos, enumeração, SMTP e configuração de produção.
5. Flyway, backup/restauração, volume de uploads e URL pública do backend antes de dados reais.
6. A página de suporte ainda exibe o WhatsApp `(65) 99999-0000`, aparentemente demonstrativo, e promete resposta em até 24 horas úteis. Confirmar canal e prazo reais antes de divulgar o produto.
