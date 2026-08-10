# Deploy — Delibera

## Primeiro deploy (bootstrap do ROOT)

No primeiro deploy em produção, o banco estará vazio. Para criar o
usuário administrador (ROOT), configure duas variáveis de ambiente
**antes** de iniciar a aplicação:

```bash
DELIBERA_ROOT_EMAIL=admin@suainstituicao.edu.br
DELIBERA_ROOT_PASSWORD=SuaSenhaSegura123!
```

### Requisitos da senha

- Mínimo 12 caracteres
- Pelo menos 1 letra maiúscula
- Pelo menos 1 letra minúscula
- Pelo menos 1 número
- Pelo menos 1 caractere especial (`!@#$%^&*` etc.)

Se a senha não atender, a aplicação **não sobe** e loga o motivo.

### Passo a passo

1. Configure as variáveis de ambiente na plataforma (Railway, Render, etc.):
   ```
   SPRING_PROFILES_ACTIVE=prod
   DELIBERA_ROOT_EMAIL=admin@suainstituicao.edu.br
   DELIBERA_ROOT_PASSWORD=SuaSenhaSegura123!
   DATABASE_URL=jdbc:postgresql://host:5432/delibera
   DB_USER=delibera
   DB_PASS=<senha-do-banco>
   DELIBERA_BASE_URL=https://app.seudominio.com.br
   UPLOAD_DIR=/app/uploads
   ```

2. Faça o deploy. No log de inicialização, confirme:
   ```
   [BOOTSTRAP] Usuário ROOT criado com email: admin@suainstituicao.edu.br
   ```

3. Acesse `/login` e autentique com o email/senha definidos.

4. **Remova** `DELIBERA_ROOT_EMAIL` e `DELIBERA_ROOT_PASSWORD` das
   variáveis de ambiente. Deploys subsequentes sem essas vars são
   silenciosamente ignorados (o bootstrap não roda de novo).

### Deploys subsequentes

Com as variáveis removidas, o bootstrap é ignorado. Se precisar
criar outro ROOT, basta definir as vars novamente — o sistema
verifica por email se o usuário já existe e não sobrescreve.

## Variáveis de ambiente (produção)

| Variável | Obrigatória | Descrição |
|----------|:-----------:|-----------|
| `SPRING_PROFILES_ACTIVE` | Sim | Deve ser `prod` |
| `DATABASE_URL` | Sim | URL JDBC do PostgreSQL |
| `DB_USER` | Sim | Usuário do banco |
| `DB_PASS` | Sim | Senha do banco |
| `DELIBERA_ROOT_EMAIL` | Apenas 1º deploy | Email do administrador ROOT |
| `DELIBERA_ROOT_PASSWORD` | Apenas 1º deploy | Senha do ROOT (ver requisitos acima) |
| `MAIL_HOST` | Sim | Host SMTP |
| `MAIL_PORT` | Sim | Porta SMTP |
| `MAIL_USER` | Sim | Usuário SMTP |
| `MAIL_PASS` | Sim | Senha SMTP |
| `MAIL_FROM` | Não | Remetente; padrão `contato@appdelibera.com.br` |
| `EMAIL_ENABLED` | Não | Habilita SMTP; padrão `true` em produção |
| `MAIL_HEALTH_ENABLED` | Não | Inclui SMTP no health check; padrão `true` em produção |
| `DELIBERA_BASE_URL` | Sim | URL pública usada nos emails |
| `UPLOAD_DIR` | Sim | Diretório do volume persistente de anexos |
| `PORT` | Não | Porta HTTP fornecida pela plataforma |

## Volume persistente

Anexos são gravados no filesystem. Em Railway/Render, monte um volume persistente em `/app/uploads` e mantenha `UPLOAD_DIR=/app/uploads`. Sem volume, anexos podem desaparecer a cada novo deploy.

## Verificação pós-deploy

1. Confirme `UP` em `/actuator/health`.
2. Entre em `/login` com o ROOT criado.
3. Cadastre uma solicitação com anexo e faça o download pela coordenação.
4. Valide consulta por protocolo + email e cancelamento.
5. Envie um email transacional e confirme o registro em `email_log`.
