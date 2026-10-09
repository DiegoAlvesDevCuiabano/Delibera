# Teste de tela do Delibera

O `smoke.cjs` usa Chromium para testar páginas públicas, menu móvel, criação de solicitação fictícia e consulta por protocolo e email. O script só aceita `localhost` ou `127.0.0.1`, pois grava dados na aplicação.

1. Verifique se a porta `18441` está livre; escolha outra porta alta se algum processo já a estiver usando. Não use as portas do Fiplan.
2. Com JDK 21, inicie o Delibera com perfil `h2`, `SERVER_ADDRESS=127.0.0.1`, `SERVER_PORT=18441` (ou a porta escolhida) e `EMAIL_ENABLED=false`.
3. Em `e2e/`, execute `npm ci` e `npx playwright install chromium` uma vez.
4. Execute `npm test` em `e2e/`. Se a porta mudou, configure `DELIBERA_BASE_URL=http://127.0.0.1:<porta>` antes do teste.

O teste usa os dados fictícios do seeder H2. As capturas ficam em `e2e/artifacts/`, ignorado pelo Git. Não execute contra produção nem com dados reais.
