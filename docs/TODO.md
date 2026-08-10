# Dívida Técnica — Delibera

> Atualizado após a auditoria de 10/08/2026. O bloco de correções obrigatórias
> para piloto (segurança + operação) foi aplicado; o que sobrou está abaixo.

## Obrigatório antes de dados reais

- **Deploy e SMTP:** publicar com o perfil `prod`, PostgreSQL, HTTPS, volume persistente
  e credenciais SMTP; executar o roteiro de `docs/DEPLOY.md`.
- **`TOKEN_SECRET` em produção:** obrigatório — a aplicação aborta a inicialização sem
  ele (assina os links de voucher). Gerar com `openssl rand -base64 48`.
- **Volume de uploads no Railway:** o `docker-compose.yml` já monta `delibera-uploads`,
  mas o deploy do Railway precisa do volume equivalente apontando para `UPLOAD_DIR`.
  Sem isso os anexos somem a cada redeploy.
- **Migrações de banco:** substituir `spring.jpa.hibernate.ddl-auto=update` por Flyway
  antes de evoluções frequentes de schema em produção.

## Evolução funcional

- **Gestão cross-tenant:** as roles `ROOT` e `GESTAO` existem, mas `/gestao` ainda não
  possui fluxo de cadastro e administração de instituições/usuários. Hoje colocar um
  cliente no ar exige mexer no banco à mão.
- **Relatórios:** exportação CSV/PDF por período é o artefato que sustenta a renovação;
  gráfico por tipo e indicadores avançados vêm depois.
- **Filtros do dashboard:** busca, status e paginação estão implementados. Adicionar tipo
  e intervalo de datas quando o volume justificar.

## Hardening futuro

- **CSP:** falta uma política de Content-Security-Policy; os templates ainda possuem CSS e
  JavaScript inline, então a adoção exige nonce/hash ou extração para arquivos estáticos.
- **`@Version` em `Solicitacao`:** dois coordenadores decidindo em paralelo hoje resultam
  em last-write-wins com dois emails contraditórios ao aluno.
- **Container como root:** o `Dockerfile` não define `USER`.
- **`docker-compose` sobe com perfil `dev`,** o que executa o `DataSeeder` e cria contas
  com senha conhecida. Não usar esse compose fora da máquina local.
- **DOCX:** o upload valida extensão, MIME e assinatura ZIP via Tika Core. Para inspeção
  estrutural completa de OOXML, adotar `tika-parsers-standard-package` e testar arquivos
  produzidos por Word, Google Docs e LibreOffice.
- **Rate limiting distribuído:** a implementação é em memória, com teto de 50k IPs e
  varredura de expirados. Adequada a uma instância; migrar para Redis ao escalar.
- **Arquivos em object storage:** considerar S3 compatível quando houver múltiplas
  instâncias ou necessidade de backup/versionamento.

## Regras que não podem regredir

- `SecurityConfig` termina em `anyRequest().authenticated()`. Com `permitAll()`, todo
  controller novo nasce público — foi assim que `/painel` virou um 500 aberto a anônimos.
- `IpExtractor` não lê `X-Forwarded-For` diretamente. Em produção, quem trata o header é
  o `ForwardedHeaderFilter` via `server.forward-headers-strategy=framework`.
- O alvo do forward do `RateLimitFilter` precisa aceitar POST. Restrito a GET, cada
  requisição bloqueada gravava um `ErroLog` e disparava um email de alerta.
- Links acionáveis enviados por email usam token assinado e ação via POST. Com GET,
  scanners de link corporativos consomem o recurso antes do destinatário abrir a mensagem.
