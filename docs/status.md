# Delibera — Status Completo do Projeto

> Última atualização: 19/04/2026
> Contexto: 743k/1000k tokens consumidos na conversa atual

---

## O que é o Delibera

SaaS multi-tenant para gestão de solicitações acadêmicas em instituições de ensino (faculdades pequenas, centros universitários). Substitui o fluxo fragmentado de WhatsApp/email/papel por um sistema com protocolo, histórico e decisão registrada.

**Público:** Coordenações acadêmicas (quem paga) + alunos (quem usa gratuitamente, sem login).

**Modelo de venda:** Consultivo (não self-service). Piloto de 30 dias gratuito. Preço: R$ 349/mês (anual) ou R$ 399/mês (mensal). Plano único durante fase de validação.

**Repositórios:**
- Código: `DiegoAlvesDevCuiabano/Delibera` (privado)
- Demo: `DiegoAlvesDevCuiabano/Delibera-Demo` (público, GitHub Pages)
- URL demo: https://diegoalvesdevcuiabano.github.io/Delibera-Demo/

---

## Stack Técnica

| Camada | Tecnologia |
|--------|-----------|
| Backend | Spring Boot 3.4.1 / Java 21 |
| Frontend | Thymeleaf + Bootstrap 5.3 + Bootstrap Icons |
| Banco | PostgreSQL 16 (Docker) / H2 (dev sem Docker) |
| CSS | Plus Jakarta Sans + delibera.css (paleta v3) |
| Date Picker | Flatpickr com tema customizado |
| Email | Spring Mail (modo simulado por padrão) |
| Build | Maven |
| Containers | Docker + docker-compose |
| Deploy alvo | Railway ou Render |

---

## Arquitetura

### Perfis de Acesso (sem ALUNO — aluno não tem login)
```
ROOT           → Acesso global, cross-tenant, owner do sistema
GESTAO         → Auxiliar delegado, cross-tenant controlado
ADMIN_INSTITUICAO → Gerencia usuários e config da instituição
COORDENADOR    → Analisa solicitações da sua instituição
```

### Fluxo do Aluno (público, sem autenticação)
```
/nova-solicitacao → preenche formulário → protocolo gerado
/status/{protocolo} → vê detalhe + timeline
/consultar-protocolo → busca por protocolo ou email
```

### Fluxo do Coordenador (autenticado)
```
/login → /coordenacao (redirect por role)
/coordenacao → dashboard com KPIs + inbox de pendentes
/coordenacao/solicitacoes/{id} → analisar + deferir/encaminhar/indeferir
```

### Entidades
```
Instituicao (tenant)
├── Usuario (coordenador/admin — NÃO aluno)
├── Curso
└── Solicitacao
    ├── alunoNome, alunoEmail, alunoWhatsapp (campos texto, não FK)
    ├── alunoCurso, alunoTurma (campos texto)
    ├── tipo (enum), status (enum), disciplina, professor
    ├── justificativa, parecer
    ├── notaAtual, notaEsperada, datasFalta (campos por tipo)
    ├── HistoricoSolicitacao (log de cada mudança de status)
    └── Anexo (upload de documentos)
```

### Enums
```java
TipoSolicitacao: ABONO_FALTA, SEGUNDA_CHAMADA, AJUSTE_PRESENCA, CONTESTACAO_NOTA
StatusSolicitacao: EM_ANALISE, AGUARDANDO_PROFESSOR, RESOLVIDO, NEGADO, CANCELADA
Role: ROOT, GESTAO, ADMIN_INSTITUICAO, COORDENADOR
```

---

## Fases Concluídas

### Fase 0 — Setup ✅
- Projeto Spring Boot 3.4.1 + Java 21
- PostgreSQL via Docker (docker-compose.yml)
- Perfil H2 para dev sem Docker (application-h2.properties)
- Dockerfile multi-stage (temurin:21)
- .gitignore, .env.example

### Fase 1 — Multi-tenancy e Auth ✅
- Entidades Instituicao, Usuario, Curso com campos de auditoria
- Usuario: instituicao nullable para ROOT, helpers isCrossTenant()
- UsuarioDetailsService com verificação de ativo
- SecurityConfig com rotas por role (hasAnyRole)
- AuthSuccessHandler: ROOT→/gestao, COORD→/coordenacao
- GlobalControllerAdvice: injeta usuarioLogado (entidade) nos templates
- Repositories com queries de tenant
- DataSeeder com ROOT + ADMIN + COORD + 5 solicitações mock
- Fix: CSRF duplicado no login corrigido

### Fase 2 — Solicitações Core ✅
- Solicitacao refatorada: aluno como campos de texto (não FK)
- Campos específicos por tipo: notaAtual, notaEsperada, datasFalta
- SolicitacaoService: criar, deferir, indeferir, encaminhar, cancelar
- Protocolo gerado automaticamente (DEL-{ano}-{6 dígitos})
- SolicitacaoPublicController: /nova-solicitacao, /consultar-protocolo, /status/{protocolo}
- NovaSolicitacaoDTO com Bean Validation (@Valid, @Email, @Size)
- SolicitacaoRepository com queries por tenant e email
- Fix: LazyInitializationException (force fetch com @Transactional)

### Fase 3 — Coordenação ✅
- Dashboard: KPIs (pendentes, resolvidas, negadas, total)
- Inbox de pendentes com tabela (movido para antes dos gráficos)
- Tabela "Todas as solicitações"
- Tela de análise: dados do aluno, detalhes, justificativa, timeline
- 3 ações: Deferir, Encaminhar ao professor, Indeferir
- Templates rápidos de parecer (deferido padrão, indeferido padrão)
- Verificação de ownership (verificarOwnership no service)
- Rotas: /coordenacao, /coordenacao/solicitacoes/{id}, /coordenacao/solicitacoes/{id}/deferir|encaminhar|indeferir

### Fase 4 — Anexos e Notificações ✅
- Entidade Anexo (nome, contentType, tamanho, FK solicitacao)
- AnexoService: upload para filesystem, validação tipo (PDF/JPG/PNG) e tamanho (10MB)
- Upload integrado no POST /nova-solicitacao (MultipartFile)
- EmailService com @Async: notificarCriacao, notificarMudancaStatus
- Modo simulado (log) quando delibera.email.enabled=false
- Config SMTP via variáveis de ambiente
- @EnableAsync na aplicação
- application-prod.properties corrigido para PostgreSQL

### Fase 5 — Componentes Visuais e UX ✅
- Toast fragment: success/error/warning com auto-dismiss 5s
- Modal de confirmação: dinâmico via data-* attributes
- CSS: validação visual (.is-invalid), loading states (.btn-loading), empty states, disabled states, alertas inline (.alert-delibera)
- delibera.js: spinner automático em botões de submit
- Páginas de erro: 404 e 500 customizadas com visual Delibera
- CustomErrorController para dispatch correto
- Footer split: 'components' (público) vs 'components-auth' (autenticado, com modal)

### Fase 6 — Testes e Segurança ✅
- **Rate limiting:** LoginRateLimiter (5 tentativas/15min → bloqueio 30min), RateLimitFilter, LoginFailureHandler
- **Tenant isolation:** verificarOwnership() no SolicitacaoService, AccessDeniedException
- **Input validation:** NovaSolicitacaoDTO com @Valid + BindingResult
- **Headers:** HSTS, X-Frame-Options SAMEORIGIN, X-Content-Type-Options, session timeout 30min, sessão única
- **Testes unitários (8):** SolicitacaoServiceTest — criar, deferir, indeferir, encaminhar, cancelar, ownership
- **Testes segurança (10):** SecurityTest — rotas públicas 200, protegidas 302, 404
- **GlobalExceptionHandler:** AccessDenied, IllegalArgument, IllegalState
- **Total: 19 testes, BUILD SUCCESS**

---

## Conversão Design v3 ✅

Todos os 24 templates Thymeleaf convertidos de v1 (antigo) para v3 (warm/institucional):

### Identidade Visual v3
- **Logo:** 4 barras coloridas (laranja/azul/verde/navy) — narrativa "canais virando protocolo"
- **Wordmark:** *Delibera.* — Georgia serifada itálica com ponto laranja
- **Paleta:** --navy: #1e3a5f; --accent: #f97316; --blue: #3b82f6; --success: #10b981
- **Fundo:** branco + off-white quente (#faf8f6)
- **Tipografia:** Plus Jakarta Sans (corpo), Georgia (wordmark)
- **Cantos:** 16px nos cards, 12px nos inputs, 10px nos botões
- **Sombras:** suaves, sem dark mode

### Componentes Extras
- Flatpickr com tema customizado (navbar navy, seleção laranja, pt-BR)
- Ícone do calendário à direita do input
- Botão copiar protocolo com feedback visual

### Templates (24 arquivos)
```
fragments/
├── head.html (favicon v3, Flatpickr CDN)
├── navbar.html (public + app, logo 4 barras, wordmark Georgia)
├── footer.html (default + landing + components + components-auth)
├── sidebar.html (topnav + default, navy gradient)
├── logo-svg.html (icon + brand fragments)
├── toasts.html (success/error/warning)
├── modals.html (confirm modal dinâmico)
└── page-header.html

pages/public/
├── index.html (landing: hero, como funciona, antes/depois, features, validação, CTA)
├── login.html (split-screen, social login, alerts erro/bloqueio/expirado)
├── planos.html (card único R$349, FAQ, trust bar)
├── sobre.html, suporte.html, termos.html, privacidade.html

pages/aluno/
├── nova-solicitacao.html (form com section labels, condicionais por tipo, upload, LGPD)
├── consultar-protocolo.html (busca por protocolo ou email, cards resultado)
├── status-solicitacao.html (protocolo + copiar, detalhes, milestone timeline, histórico)
├── confirmacao-solicitacao.html (protocolo herói, próximos passos, resumo)
└── acompanhar.html (listagem legada)

pages/coordenacao/
├── dashboard.html (demo banner, alerta urgência, KPIs, inbox, todas solicitações)
└── analisar-solicitacao.html (breadcrumb, detalhes, anexos, decisão sticky, timeline)

error/
├── 404.html
└── 500.html
```

---

## Como Rodar

### Sem Docker (H2 em memória):
```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21"
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2
```

### Com Docker (PostgreSQL):
```bash
docker-compose up db -d
export JAVA_HOME="/c/Program Files/Java/jdk-21"
./mvnw spring-boot:run
```

### Na IDE (IntelliJ):
- Project SDK: JDK 21 (`C:\Program Files\Java\jdk-21`)
- Active profiles: `h2`
- Run DeliberaApplication

### Credenciais de teste (DataSeeder):
| Role | Email | Senha |
|------|-------|-------|
| ROOT | admin@delibera.com.br | demo123 |
| ADMIN | admin@exemplo.edu.br | demo123 |
| COORD | coordenacao@exemplo.edu.br | demo123 |

### Rodar testes:
```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21"
./mvnw test
# 19 testes, BUILD SUCCESS
```

---

## O que Falta

### Fase 7 — Deploy (não iniciada)
- [ ] Escolher plataforma (Railway ou Render)
- [ ] Configurar variáveis de ambiente em prod
- [ ] Deploy com HTTPS automático
- [ ] Smoke test em produção
- [ ] Domínio customizado (se tiver)

### Pendências conhecidas
- [ ] Email real (precisa de credenciais SMTP — hoje é modo simulado/log)
- [ ] Docker Desktop não testado (usuário não abriu Docker)
- [ ] Upload de anexo: falta rota de download (/anexos/{id}/download)
- [ ] Formulário nova-solicitacao: campos condicionais por tipo (nota atual/esperada) existem no DTO mas não no template v3
- [ ] Gestão cross-tenant: controller /gestao não implementado
- [ ] Tempo médio de resolução: KPI existe no mockup mas não calculado no backend
- [ ] Gráfico de distribuição por tipo: existe no mockup mas não implementado (Chart.js)

### Mockup v3 vs Produto — gaps visuais
- [ ] Dashboard: faltam gráfico por tipo e card de relatórios (tem no mockup, não no Thymeleaf)
- [ ] Sidebar: conteúdo simplificado vs mockup (que tem submenu de solicitações)
- [ ] Landing: mockup de dashboard no hero é estático (vs dados reais)
- [ ] Tour guiado (tour.js) existe no mockup mas não no produto

---

## Estrutura de Diretórios

```
Solicitações Acadêmicas/
├── CLAUDE.md                    # Regras de desenvolvimento
├── Dockerfile                   # Multi-stage Java 21
├── docker-compose.yml           # app + postgres
├── pom.xml                      # Maven, Spring Boot 3.4.1
├── .env.example                 # Variáveis de ambiente
├── .gitignore
├── docs/
│   └── status.md                # Este arquivo
├── Design/
│   ├── v1/                      # Design original (5 telas)
│   ├── v2/                      # Design dark/dev (5 telas)
│   ├── v3/                      # Design warm/institucional (13 telas) ← ATUAL
│   ├── v3-backup/               # Backup pré-tour
│   └── v4/                      # Híbrido v3+v2 (experimento)
├── src/main/java/com/delibera/
│   ├── DeliberaApplication.java
│   ├── config/
│   │   ├── SecurityConfig.java
│   │   ├── AuthSuccessHandler.java
│   │   ├── LoginFailureHandler.java
│   │   ├── LoginRateLimiter.java
│   │   ├── RateLimitFilter.java
│   │   ├── UsuarioDetailsService.java
│   │   ├── GlobalControllerAdvice.java
│   │   ├── CustomErrorController.java
│   │   └── DataSeeder.java
│   ├── controller/
│   │   ├── PublicController.java          # Landing, planos, sobre, etc.
│   │   ├── SolicitacaoPublicController.java # Fluxo aluno (público)
│   │   ├── CoordenacaoController.java     # Dashboard + analisar
│   │   └── PainelController.java          # Redirect /painel
│   ├── model/
│   │   ├── entity/
│   │   │   ├── Instituicao.java
│   │   │   ├── Usuario.java
│   │   │   ├── Curso.java
│   │   │   ├── Solicitacao.java
│   │   │   ├── HistoricoSolicitacao.java
│   │   │   └── Anexo.java
│   │   ├── enums/
│   │   │   ├── Role.java
│   │   │   ├── TipoSolicitacao.java
│   │   │   └── StatusSolicitacao.java
│   │   └── dto/
│   │       └── NovaSolicitacaoDTO.java
│   ├── repository/
│   │   ├── InstituicaoRepository.java
│   │   ├── UsuarioRepository.java
│   │   ├── CursoRepository.java
│   │   ├── SolicitacaoRepository.java
│   │   ├── HistoricoSolicitacaoRepository.java
│   │   └── AnexoRepository.java
│   ├── service/
│   │   ├── SolicitacaoService.java
│   │   ├── UsuarioService.java
│   │   ├── AnexoService.java
│   │   └── EmailService.java
│   └── exception/
│       ├── AccessDeniedException.java
│       └── GlobalExceptionHandler.java
├── src/main/resources/
│   ├── application.properties
│   ├── application-dev.properties    # PostgreSQL
│   ├── application-h2.properties     # H2 in-memory
│   ├── application-prod.properties   # PostgreSQL prod
│   ├── static/
│   │   ├── css/
│   │   │   ├── delibera.css          # Design system v3
│   │   │   └── flatpickr-delibera.css # Tema Flatpickr
│   │   └── js/
│   │       └── delibera.js           # Loading states + Flatpickr init
│   └── templates/                    # 24 arquivos Thymeleaf (listados acima)
└── src/test/java/com/delibera/
    ├── DeliberaApplicationTests.java
    ├── service/SolicitacaoServiceTest.java  # 8 testes unitários
    └── security/SecurityTest.java           # 10 testes de segurança
```

---

## Decisões Técnicas Relevantes

| Decisão | Escolha | Motivo |
|---------|---------|--------|
| Aluno sem login | Campos de texto na Solicitacao | Diferencial do produto — sem fricção, protocolo por email |
| PostgreSQL | Em vez de MySQL | Melhor com enums, JSON nativo, free no Railway |
| open-in-view=false | Force fetch no service | Evitar LazyInitializationException, melhor performance |
| anyRequest().permitAll() | Em vez de authenticated() | Permite 404 customizado em rotas inexistentes; rotas reais protegidas por hasAnyRole |
| Rate limiting in-memory | ConcurrentHashMap | Suficiente para MVP; trocar por Redis em produção |
| Email simulado | Log por padrão | Não temos SMTP configurado; habilitar com delibera.email.enabled=true |
| Flatpickr | Em vez de date picker nativo | Calendário nativo do browser não aceita CSS |

---

## Histórico de Commits (principais)

```
deccd85 fix: date picker — ícone à direita, placeholder, abre abaixo
dd437fb feat: Flatpickr com tema Delibera v3
32b98d2 fix: botão copiar protocolo no status + estilo do date picker
0537f98 fix: corrigir links /solicitacoes/nova → /nova-solicitacao
50c84ac feat: converter todos os templates para design v3
30a6474 feat: 6.6+6.7+6.8 — Testes unitários, integração e segurança
dbc412b feat: 6.2+6.3 — Tenant isolation + input validation
1db5a37 feat: 6.1 — Rate limiting no login + session security
7b4fd95 feat: Fase 5 — Componentes visuais e UX
b9b9297 feat: Fase 4 — Anexos e notificações por email
c6b3dad feat: Fase 3 — Coordenação completa
9b9f817 feat: Fase 2 — Solicitações core (aluno sem login)
b892774 fix: remover CSRF duplicado no login
...      Fases 0 e 1 (setup, auth, entidades)
```
