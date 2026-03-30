# CLAUDE.md - Delibera (Solicitações Acadêmicas)

> **IMPORTANTE:** Este projeto faz parte de um portfólio de projetos monetizáveis. Consultar `../RESUMO.md` para visão geral de todos os projetos e prioridades.

## Project Overview

**Nome:** Delibera - Gestão de Solicitações Acadêmicas
**Tipo:** SaaS multi-tenant para instituições de ensino
**Objetivo:** Centralizar solicitações acadêmicas (abono de falta, segunda chamada, contestação de nota) em um fluxo único e rastreável, substituindo email/WhatsApp/papel.
**Público:** Alunos e coordenação acadêmica

## Tech Stack (recomendada)

- **Backend:** Spring Boot 3.2+ / Java 21
- **Frontend:** Thymeleaf + Bootstrap 5.3 + Bootstrap Icons
- **Database:** MySQL 8.0+ (ou PostgreSQL)
- **PDF:** iText 5.x
- **Email:** Spring Mail (SMTP)
- **Build:** Maven
- **Deploy alvo:** Railway ou Render (HTTPS automático)

## Referência: Projeto Comercial UniSENAI

Este projeto é baseado na experiência e padrões do **Sistema de Controle Comercial UniSENAI** (repositório `DiegoAlvesDevCuiabano/Comercial`). Reutilizar padrões de lá sempre que possível.

### O que reutilizar do Comercial:
- Estrutura de pacotes: `config/`, `controller/`, `service/`, `repository/`, `model/entity/`, `model/dto/`, `exception/`, `util/`
- SecurityConfig com form-based auth + BCrypt + CSRF
- GlobalControllerAdvice para injetar userDetails e currentUri
- GlobalExceptionHandler com custom exceptions
- Padrão de DTOs tipados (não usar HashMap<String, Object>)
- Toast fragments para feedback (success/error via flash attributes)
- PDF viewer com iframe wrapper para título correto na aba
- EmailService com MimeMessage e anexo PDF
- RelatorioGenerator com helpers compartilhados (cabecalho, secao, rodape, zebra)
- CSS com variáveis, gradiente SVG background, menu ativo

### O que NÃO copiar do Comercial:
- Double para valores monetários (usar BigDecimal desde o início)
- @Transactional no controller (sempre no service)
- HashMap manual para respostas JSON (usar DTOs)
- Logger criado dentro de método (usar campo estático)
- CSS inline duplicado em templates (usar stylesheet global)

## Arquitetura

### Domain Model

```
Instituicao (tenant)
├── Usuario (aluno ou coordenador)
├── Curso
└── Solicitacao
    ├── TipoSolicitacao (enum: ABONO_FALTA, SEGUNDA_CHAMADA, AJUSTE_PRESENCA, CONTESTACAO_NOTA)
    ├── StatusSolicitacao (enum: EM_ANALISE, AGUARDANDO_PROFESSOR, RESOLVIDO, NEGADO, CANCELADA)
    ├── HistoricoSolicitacao (log de cada mudança de status)
    └── Anexo (upload de documentos)
```

### Roles
- **ALUNO:** cria solicitação, acompanha status, cancela, faz upload
- **COORDENADOR:** avalia, encaminha, aprova, nega, visualiza dashboard
- **ADMIN_INSTITUICAO:** gerencia usuários e configurações da instituição

### Fluxo de Status
```
ALUNO cria → EM_ANALISE
  → COORDENADOR resolve direto → RESOLVIDO ou NEGADO
  → COORDENADOR encaminha → AGUARDANDO_PROFESSOR → RESOLVIDO ou NEGADO
  → ALUNO cancela (a qualquer momento antes do desfecho) → CANCELADA
```

## Build and Run

```bash
./mvnw spring-boot:run        # desenvolvimento
./mvnw clean package           # build
./mvnw test                    # testes
./mvnw clean package -DskipTests  # build sem testes
```

## Regras de Desenvolvimento

### Commits
- Conventional Commits em português: `feat:`, `fix:`, `refactor:`, `chore:`, `docs:`
- Commits atômicos (1 mudança lógica por commit)
- SEM Co-Authored-By ou referência ao Claude nos commits
- Mensagem descritiva com corpo explicativo quando necessário

### Git Workflow
- Branch `develop` como principal
- Feature branches: `feat/nome-feature`
- Fix branches: `fix/nome-fix`
- Merge com `--no-ff` para preservar histórico visual
- Deletar branch após merge (local e remota)
- NÃO commitar sem o usuário pedir
- Acumular mudanças e commitar quando o bloco estiver pronto

### Código
- Entidades com Lombok (@Getter @Setter)
- BigDecimal para qualquer valor monetário
- @Transactional apenas no service layer
- DTOs para respostas JSON (nunca expor entidades diretamente)
- Logger como campo estático: `private static final Logger logger = LoggerFactory.getLogger(Classe.class);`
- Validação no service com custom exceptions
- Constructor injection (não field injection)

### Frontend
- Thymeleaf com fragments reutilizáveis (header, menu, footer, toasts)
- CSS global em arquivo separado (não inline nos templates)
- Variáveis CSS para paleta de cores
- Modais Bootstrap para confirmações (NUNCA usar alert/confirm do JS)
- Event delegation para botões dinâmicos
- Usar `data-*` attributes ao invés de `th:onclick` com strings (Thymeleaf 3.1 bloqueia)

### Testes
- Testes unitários com Mockito para services
- Cobertura mínima: services e lógica de negócio

## Gotchas Conhecidos (Thymeleaf 3.1 / Spring Boot 3.x)

1. **`#request` removido dos templates** — Não usar `#httpServletRequest` nem `#request.requestURI`. Injetar via `@ModelAttribute` no GlobalControllerAdvice.

2. **`th:onclick` com strings bloqueado** — "Only variable expressions returning numbers or booleans are allowed". Usar `data-*` attributes + JS.

3. **Chrome ignora filename de PDFs inline** — Criar HTML wrapper com `<iframe src="/rota/raw">` e `<title>` desejado.

4. **X-Frame-Options DENY bloqueia iframes do próprio site** — Usar `sameOrigin()` no SecurityConfig.

5. **CSRF em forms dinâmicos** — Forms com action setado via JS não recebem token CSRF automático. Adicionar `<input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}">`.

6. **MySQL ENUM vs VARCHAR** — Se usar `@Enumerated(EnumType.STRING)`, o Hibernate pode criar ENUM no MySQL. Ao adicionar novo valor no enum Java, o banco rejeita. Preferir `@Column(length = 30)` para forçar VARCHAR.

7. **`@DateTimeFormat` com strings vazias** — Formulários enviam `""` para campos date não preenchidos. `LocalDate` com `@DateTimeFormat` trata como null, mas verificar.

## Segurança e LGPD

### Segurança obrigatória (MVP)
- BCrypt para senhas (nunca armazenar plaintext)
- CSRF ativado em todos os formulários
- Security headers: X-Frame-Options SAMEORIGIN, X-Content-Type-Options
- Credenciais via variáveis de ambiente (nunca hardcoded)
- Swagger desabilitado em produção
- Rate limiting no login (implementar antes do deploy)
- Validação de input em todos os endpoints
- Verificação de ownership (aluno só vê suas solicitações, coordenador só vê da sua instituição)

### LGPD (dados de alunos são dados pessoais)
- **Consentimento:** Termo de uso na criação de conta
- **Finalidade:** Coletar apenas dados necessários para o fluxo acadêmico
- **Minimização:** Não pedir dados desnecessários (CPF só se obrigatório)
- **Acesso:** Aluno pode visualizar todos os seus dados
- **Exclusão:** Implementar mecanismo de exclusão de conta (soft delete + anonimização após período)
- **Portabilidade:** Aluno pode exportar suas solicitações
- **Segurança:** Criptografar dados sensíveis em trânsito (HTTPS) e em repouso (campos sensíveis)
- **Retenção:** Definir política de retenção (ex: 5 anos após formatura, depois anonimizar)
- **Incidentes:** Plano de resposta a incidentes de dados
- **DPO:** Instituição deve indicar encarregado de dados

### Multi-tenancy
- Cada instituição é um tenant isolado
- Dados nunca devem vazar entre tenants
- Filtrar TODAS as queries por `instituicao_id`
- Usar Hibernate Filters ou Spring Data JPA Specifications
- Testar isolamento com dados de múltiplas instituições

## Design System

### Paleta (do mockup Delibera)
```css
:root {
  --primary-blue: #0e355a;
  --secondary-blue: #032542;
  --accent-orange: #d96f19;
  --success-green: #439f36;
  --bg-dark: #08355A;
  --bg-medium: #17476F;
  --text-muted: #6c757d;
}
```

### Telas já desenhadas (HTML estático em /Design)
- index.html — landing page
- login.html — autenticação
- nova-solicitacao.html — wizard de criação
- acompanhar.html — lista de solicitações do aluno
- status-solicitacao.html — detalhe de uma solicitação
- confirmacao-solicitacao.html — confirmação após criar
- dashboard-coordenacao.html — dashboard com KPIs
- analisar-solicitacao.html — coordenador avalia
- planos.html — página de planos/assinatura
- sobre.html, suporte.html, termos.html, privacidade.html

## Plano de Execução (MVP)

### Fase 0 — Setup (~1h)
- [ ] Criar projeto Spring Boot com dependências
- [ ] Configurar MySQL + application.properties
- [ ] SecurityConfig com roles ALUNO/COORDENADOR
- [ ] Estrutura de pacotes
- [ ] Converter telas HTML para Thymeleaf fragments

### Fase 1 — Multi-tenancy e Auth (~4h)
- [ ] Entidades: Instituicao, Usuario, Curso
- [ ] Enum Role (ALUNO, COORDENADOR, ADMIN)
- [ ] UsuarioDetailsService com roles e tenant
- [ ] Login/logout com redirecionamento por role
- [ ] Filtro de tenant em todas as queries

### Fase 2 — Solicitações (core) (~6h)
- [ ] Entidades: Solicitacao, TipoSolicitacao, StatusSolicitacao
- [ ] SolicitacaoService com fluxo de status
- [ ] Controller para CRUD de solicitações
- [ ] Wizard de nova solicitação (Thymeleaf)
- [ ] Listagem e filtro para aluno
- [ ] Detalhe com histórico de status

### Fase 3 — Coordenação (~4h)
- [ ] Dashboard com KPIs (total, pendentes, resolvidas, tempo médio)
- [ ] Listagem de solicitações pendentes
- [ ] Tela de análise com ações (resolver, encaminhar, negar)
- [ ] Histórico de mudanças de status (HistoricoSolicitacao)

### Fase 4 — Anexos e Notificações (~3h)
- [ ] Upload de arquivos (MultipartFile)
- [ ] Armazenamento em filesystem ou S3
- [ ] Notificação por email (mudança de status)
- [ ] PDF de protocolo/comprovante

### Fase 5 — Componentes Visuais e UX (~3-4h)
> Componentes que os mockups não cobrem mas são essenciais para um SaaS funcional.
> Seguir a identidade visual dos mockups (paleta, sombras, bordas, tipografia).

- [ ] **Toast fragment** — notificação flutuante (canto superior direito), auto-dismiss 5s, variantes: success (verde), error (vermelho), warning (laranja), info (azul). Usar flash attributes do Spring + fragment Thymeleaf.
- [ ] **Validação visual em forms** — bordas vermelhas em campos inválidos, mensagem de erro abaixo do campo, ícone de erro. Integrar com Bean Validation (`@Valid` + `BindingResult`) e feedback visual via CSS (`.is-invalid` + `.invalid-feedback`).
- [ ] **Loading states** — spinner nos botões de submit (desabilitar botão + mostrar spinner durante requisição), skeleton/placeholder em tabelas enquanto carrega.
- [ ] **Empty states** — ilustração ou ícone + mensagem quando lista de solicitações está vazia ("Nenhuma solicitação encontrada"), tanto para aluno quanto coordenador.
- [ ] **Disabled states** — botões desabilitados com visual claro (opacity + cursor not-allowed) para ações indisponíveis (ex: cancelar solicitação já resolvida).
- [ ] **Alertas inline dismissíveis** — para mensagens contextuais dentro de cards (ex: "Você tem 3 solicitações pendentes de análise"), com botão de fechar, seguindo paleta Delibera.
- [ ] **Páginas de erro customizadas** — 404 (não encontrado) e 500 (erro interno) com visual Delibera, mensagem amigável, botão de voltar. Configurar via `ErrorController` ou páginas em `/error/`.
- [ ] **Confirmação de ações destrutivas** — modal Bootstrap estilizado (paleta Delibera) para cancelar solicitação, negar solicitação, etc. Reutilizar fragment de modal com conteúdo dinâmico via `data-*` attributes.

### Fase 6 — Testes e Segurança (~2h)
- [ ] Testes unitários dos services (Mockito)
- [ ] Rate limiting no login
- [ ] Ajustes de segurança para deploy
- [ ] Validação de ownership em todos os endpoints
- [ ] Revisão de responsividade nas telas principais

### Fase 7 — Deploy (~2h)
- [ ] Dockerfile ou Procfile
- [ ] Variáveis de ambiente
- [ ] Deploy em Railway/Render
- [ ] Teste de smoke em produção

**Total estimado: ~24-26h de pair programming com IA**

## Modification Rules
- Nunca modificar entidades ou mapeamentos JPA sem aprovação explícita
- Preferir mudanças no service layer primeiro
- Para refactors, fornecer resumo antes/depois
- Não criar arquivos de documentação (.md) a menos que pedido

## Out of Scope (MVP)
- App mobile
- Integração com sistemas acadêmicos (TOTVS, etc.)
- Chat/mensagens entre aluno e coordenador
- Assinatura digital
- Pagamento online (planos são controlados manualmente no MVP)

## Communication
- Respostas diretas no chat (não criar .md para análises)
- Ser conciso e ir ao ponto
- Não commitar sem pedir
- Acumular mudanças e commitar em blocos lógicos
