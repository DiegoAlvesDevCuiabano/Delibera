# Delibera

Sistema de gestão de solicitações acadêmicas para instituições de ensino. O aluno abre e acompanha uma solicitação por protocolo; a coordenação analisa, registra a decisão e consulta o histórico.

> **Estado:** núcleo do MVP implementado e em homologação. A aplicação ainda não está pronta para um piloto com dados reais. [Status técnico e pendências](docs/status.md) · [Demonstração visual](https://diegoalvesdevcuiabano.github.io/Delibera-Demo/landing.html)

## O que existe hoje

- Fluxo do aluno para criar, consultar e cancelar solicitações, com anexos e protocolo.
- Painel de coordenação com filtros, análise, encaminhamento e decisão.
- Resposta de professor por código temporário, perfis administrativos e isolamento por instituição.
- Testes de regras, acesso, validação e segurança; roteiro de homologação em [`docs/`](docs/).

A demonstração é **estática**: permite conhecer as telas, mas não executa as regras deste backend.

## Stack

Java 21 · Spring Boot 3.4 · Thymeleaf · Spring Security · Spring Data JPA · PostgreSQL · Maven. O perfil H2 serve para desenvolvimento e testes isolados.

## Executar localmente

Pré-requisitos: JDK 21. Para explorar a aplicação sem PostgreSQL ou SMTP real, use o perfil descartável `h2`:

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=h2
```

Em Linux/macOS, substitua `.\mvnw.cmd` por `./mvnw`. O perfil padrão `dev` usa PostgreSQL e configuração de e-mail; veja [`.env.example`](.env.example) e [`docs/`](docs/) antes de usá-lo. Use somente dados fictícios no ambiente local.

```powershell
.\mvnw.cmd test
.\mvnw.cmd clean verify
```

## Antes de um piloto real

Ainda precisam ser homologados PostgreSQL, HTTPS, envio de e-mail, armazenamento persistente de anexos e backup/restauração. Migração controlada de esquema e provisionamento de instituições também são pendências. Consulte [`docs/status.md`](docs/status.md), [`docs/TODO.md`](docs/TODO.md) e o [roteiro de deploy](docs/DEPLOY.md) para o estado mais recente. Não cadastre dados de alunos reais nesta instalação de desenvolvimento.

## Estrutura

- `src/main/java/com/delibera/`: controladores, serviços, segurança, entidades e repositórios.
- `src/main/resources/templates/`: páginas Thymeleaf; `static/`: CSS e JavaScript.
- `src/test/`: testes automatizados.
- `docs/`: estado, homologação, operação e pendências.
- `Design/`: protótipos visuais.
