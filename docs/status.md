# Delibera — Status de Construção

> Última atualização: 19/04/2026

## Fase atual: 0 — Setup
**Status:** Não iniciada

---

## Progresso por Fase

| Fase | Descrição | Status | Início | Fim |
|------|-----------|--------|--------|-----|
| 0 | Setup do projeto | Em andamento | 19/04 | — |
| 1 | Multi-tenancy e Auth | Não iniciada | — | — |
| 2 | Solicitações (core) | Não iniciada | — | — |
| 3 | Coordenação | Não iniciada | — | — |
| 4 | Anexos e Notificações | Não iniciada | — | — |
| 5 | Componentes Visuais e UX | Não iniciada | — | — |
| 6 | Testes e Segurança | Não iniciada | — | — |
| 7 | Deploy | Não iniciada | — | — |

---

## Worklog

### Fase 0 — Setup

| Data | O que foi feito | Arquivos |
|------|-----------------|----------|
| 19/04 | pom.xml: MySQL→PostgreSQL, Java 17→21 | pom.xml |
| 19/04 | Perfis: +ROOT, +GESTAO, -ALUNO | Role.java |
| 19/04 | SecurityConfig: rotas aluno públicas + novos perfis | SecurityConfig.java |
| 19/04 | Dev properties: PostgreSQL via Docker | application-dev.properties |
| 19/04 | Docker: compose + Dockerfile Java 21 + .env.example | docker-compose.yml, Dockerfile, .env.example |

---

## Decisões técnicas

| Decisão | Escolha | Motivo |
|---------|---------|--------|
| Framework | Spring Boot 3.4.1 / Java 21 | Stack do portfólio, referência do Comercial UniSENAI |
| Frontend | Thymeleaf + Bootstrap 5.3 | Converter mockups v3 diretamente |
| Banco | PostgreSQL 16 | Melhor com enums, JSON nativo, free no Railway |
| Build | Maven | Padrão do ecossistema Spring |
| Containers | Docker + docker-compose | Postgres + app em dev, Dockerfile para deploy |
| Deploy | Railway ou Render | HTTPS automático, free tier para validação |

## Referências

- **Mockups:** `Design/v3/` (13 telas HTML)
- **Demo pública:** https://diegoalvesdevcuiabano.github.io/Delibera-Demo/
- **Projeto referência:** `DiegoAlvesDevCuiabano/Comercial` (padrões de código)
- **Regras de negócio:** `CLAUDE.md`
