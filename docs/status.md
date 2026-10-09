# Delibera — Mapa e Status do Projeto

> Atualizado em 09/10/2026. Delibera é o foco ativo da frente de produtos pessoais.

## Visão geral

O Delibera é um SaaS multi-tenant para solicitações acadêmicas. Alunos usam o fluxo público sem conta; coordenação, administração e gestão usam autenticação por roles. A aplicação é um monólito Spring Boot com páginas Thymeleaf e PostgreSQL em produção.

## Mapa técnico

```text
src/main/java/com/delibera/
├── config/       segurança, rate limit, bootstrap e jobs
├── controller/   fluxos público, coordenação, professor e ROOT
├── service/      regras de solicitação, anexos, email e auditoria
├── repository/   acesso JPA com filtros de tenant
├── model/        entidades, DTOs e enums
├── exception/    tratamento global de erros
└── util/         senha e extração segura de IP

src/main/resources/
├── templates/    páginas e fragments Thymeleaf
├── static/       CSS e JavaScript
└── application-*.properties
```

Protótipos estáticos ficam em `Design/`; testes ficam em `src/test/java/com/delibera`; instruções operacionais ficam em `docs/`.

## Fluxos implementados

- **Aluno:** criar solicitação, anexar PDF/JPG/PNG/DOCX, receber protocolo, consultar com protocolo + email, recuperar protocolos por link assinado, listar pedidos, exportar os próprios dados, solicitar exclusão e cancelar enquanto pendente.
- **Coordenação:** dashboard com KPIs, busca, filtro por status e paginação; análise, deferimento, indeferimento, encaminhamento e download de anexos com ownership.
- **Professor:** resposta pública por protocolo + código de uso único e expiração.
- **ROOT:** painel global, acompanhamento de erros e bootstrap seguro via variáveis de ambiente.
- **Operação:** emails registrados, alertas de erro, auditoria, piloto/voucher, feedback, rate limiting, health check e anonimização agendada.

## Segurança e privacidade

Há isolamento por instituição, BCrypt, sessão única, CSRF, headers de segurança, validação Bean Validation, detecção de conteúdo de anexos com Tika e respostas públicas minimizadas por DTO. O status público exige protocolo e email. Feedback exige autenticação e token CSRF.

O `SecurityConfig` é default-deny: rota sem matcher explícito exige autenticação. O rate limiting usa o IP resolvido pelo container (não o header `X-Forwarded-For` cru) e devolve 429 com página própria. Links acionáveis por email usam token HMAC com prazo e exigem POST. A coordenação não consegue decidir solicitação em estado final, e o parecer do professor só é aceito enquanto a solicitação aguarda parecer.

O fluxo do titular permite exportar os próprios dados em JSON e solicitar exclusão das solicitações finalizadas. Após o prazo de retenção, a anonimização alcança solicitação, histórico, logs de email, pareceres do professor e arquivos de anexo. Ver `docs/TODO.md` para os riscos residuais e requisitos de operação.

## Execução local

```powershell
# Ambiente completo: PostgreSQL no Docker + aplicação na IDE/Maven
docker compose up -d db
.\mvnw.cmd spring-boot:run

# Alternativa descartável sem Docker nem SMTP real
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=h2
.\mvnw.cmd test
.\mvnw.cmd clean verify
```

Use JDK 21. O perfil padrão é `dev`: exige PostgreSQL em `localhost:5432` e tenta
enviar emails reais. Configure `MAIL_PASS` por variável de ambiente; nunca versione
a credencial. O perfil `h2` permanece disponível para testes isolados.

## Estado de entrega

O núcleo do MVP está implementado. Em 09/10/2026, **199 testes Java passaram, sem falhas ou erros**, com Java 21 e perfil H2; a matriz está em [`HOMOLOGACAO-2026-10-09.md`](HOMOLOGACAO-2026-10-09.md). O Playwright passou no fluxo público de solicitação e consulta e na ajuda, incluindo navegação em largura móvel. CI executa em `main`, `develop` e `prod`; Railway usa `/actuator/health`. Configuração de produção aceita banco, SMTP, URL pública, diretório de upload e `TOKEN_SECRET` por ambiente.

O backend foi acessado localmente em `http://127.0.0.1:18441/` com H2. A demonstração estática está em `https://diegoalvesdevcuiabano.github.io/Delibera-Demo/landing.html`. Ainda não foi confirmada uma URL pública para o backend; a demonstração não executa as regras de negócio.

### Homologação em andamento

| Frente | Situação em 09/10/2026 |
| --- | --- |
| Isolamento entre instituições | Testes de detalhe, decisão, dashboard, CSV e download de anexo passaram com duas instituições. |
| Perfis e permissões | ROOT, gestão, admin da instituição e coordenação verificados em rotas autorizadas e proibidas; professor pelo fluxo de código. |
| Ajuda e suporte | FAQ e envio HTMX passaram no navegador; validação de campos, CSRF e cota por IP passaram no backend. |
| Respostas | Parecer do professor, código de uso único, decisão final e CSRF passaram. Erro 500 por carregamento tardio de associação JPA foi corrigido. |
| Carga e desempenho | 1.000 GETs locais H2 sem erro HTTP; p95 de 304 ms na página inicial com 20 conexões. Não representa produção. |
| Segurança | Cobertura adicional de CSRF, acesso entre instituições, código de parecer e cota; pentest e homologação em PostgreSQL/HTTPS continuam pendentes. |

**O código está pronto para homologação, mas o ambiente ainda não está pronto para piloto com dados reais.** Antes do primeiro uso real é necessário concluir o roteiro de deploy, configurar PostgreSQL/SMTP/HTTPS, criar o `TOKEN_SECRET`, montar o volume persistente de uploads e validar backup/restauração. Flyway e o provisionamento cross-tenant continuam como as principais lacunas técnicas e operacionais. Lista completa e priorizada em `docs/TODO.md`.
