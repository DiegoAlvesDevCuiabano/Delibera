# Delibera — Mapa e Status do Projeto

> Atualizado em 10/08/2026. Estado local validado com Java 21 e perfil H2.

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
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=h2
.\mvnw.cmd test
.\mvnw.cmd clean verify
```

Use JDK 21. Para PostgreSQL, execute `docker compose up --build`.

## Estado de entrega

O núcleo do MVP está implementado e coberto por 180 testes unitários, de integração e segurança — incluindo renderização real das páginas autenticadas, que antes não era exercitada. O `clean verify` completo passou com Java 21 em 10/08/2026. CI executa em `main`, `develop` e `prod`; Railway usa `/actuator/health`. Configuração de produção aceita banco, SMTP, URL pública, diretório de upload e `TOKEN_SECRET` por ambiente.

**O código está pronto para homologação, mas o ambiente ainda não está pronto para piloto com dados reais.** Antes do primeiro uso real é necessário concluir o roteiro de deploy, configurar PostgreSQL/SMTP/HTTPS, criar o `TOKEN_SECRET`, montar o volume persistente de uploads e validar backup/restauração. Flyway e o provisionamento cross-tenant continuam como as principais lacunas técnicas e operacionais. Lista completa e priorizada em `docs/TODO.md`.
