// Smoke test do fluxo do aluno com banco H2 descartável. Rode somente em localhost.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');

const base = process.env.DELIBERA_BASE_URL || 'http://127.0.0.1:18441';
assert.ok(['127.0.0.1', 'localhost'].includes(new URL(base).hostname),
  'O fluxo que cria dados fictícios só pode rodar contra localhost.');
const browserPath = process.env.PLAYWRIGHT_CHROMIUM;
const output = path.join(__dirname, 'artifacts');
fs.mkdirSync(output, { recursive: true });

async function open(page, route, heading) {
  const response = await page.goto(base + route, { waitUntil: 'domcontentloaded' });
  assert.equal(response?.status(), 200, `${route}: HTTP ${response?.status()}`);
  await page.getByRole('heading', { name: heading }).first().waitFor({ state: 'visible' });
  console.log(`OK ${route}: HTTP 200, título visível`);
}

(async () => {
  const browser = await chromium.launch({
    headless: true,
    ...(browserPath ? { executablePath: browserPath } : {}),
  });
  try {
    const page = await browser.newPage({ viewport: { width: 1365, height: 850 } });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));

    await open(page, '/', /Segunda chamada por WhatsApp/);
    await page.screenshot({ path: path.join(output, 'inicio-desktop.png'), animations: 'disabled' });

    await open(page, '/login', 'Acesso da Coordenação');
    assert.equal(await page.locator('input[name="username"]').isVisible(), true);
    assert.equal(await page.locator('input[name="password"]').isVisible(), true);

    await open(page, '/nova-solicitacao', 'Nova Solicitação Acadêmica');
    assert.equal(await page.locator('select[name="instituicaoId"]').isVisible(), true);
    assert.equal(await page.locator('select[name="tipo"]').isVisible(), true);
    console.log(`Instituições disponíveis no formulário: ${await page.locator('select[name="instituicaoId"] option').count() - 1}`);
    await page.screenshot({ path: path.join(output, 'nova-solicitacao-desktop.png'), animations: 'disabled' });

    await page.setViewportSize({ width: 390, height: 844 });
    await open(page, '/nova-solicitacao', 'Nova Solicitação Acadêmica');
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 2), true,
      'Formulário com rolagem horizontal em 390px');
    await page.screenshot({ path: path.join(output, 'nova-solicitacao-mobile.png'), animations: 'disabled' });
    console.log(`Bootstrap JS no formulário: ${await page.evaluate(() => Boolean(window.bootstrap))}`);
    await page.locator('.navbar-toggler').first().click();
    await page.locator('#navbarPublic').getByRole('link', { name: 'Nova Solicitacao' }).waitFor({ state: 'visible', timeout: 5000 });
    console.log('OK mobile: sem rolagem horizontal; menu abriu');

    const institution = await page.locator('select[name="instituicaoId"] option').nth(1).getAttribute('value');
    assert.ok(institution, 'Nenhuma instituição de teste disponível');
    await page.locator('select[name="instituicaoId"]').selectOption(institution);
    await page.locator('select[name="tipo"]').selectOption('ABONO_FALTA');
    await page.locator('input[name="alunoNome"]').fill('Aluno de Teste E2E');
    await page.locator('input[name="alunoEmail"]').fill('teste.e2e@example.test');
    await page.locator('input[name="disciplina"]').fill('Disciplina de Teste');
    const dateField = page.locator('#fieldDataOcorrencia input:not([type="hidden"])');
    const yesterday = new Date();
    yesterday.setDate(yesterday.getDate() - 1);
    const dateText = [yesterday.getDate(), yesterday.getMonth() + 1, yesterday.getFullYear()]
      .map((value, index) => index < 2 ? String(value).padStart(2, '0') : String(value)).join('/');
    await dateField.fill(dateText);
    await dateField.press('Tab');
    await page.locator('textarea[name="justificativa"]').fill('Solicitação fictícia para validar o fluxo local.');
    await page.locator('input[name="aceiteTermos"]').check();
    await page.getByRole('button', { name: 'Enviar solicitação' }).click();
    await page.waitForURL(/\/status\/DEL-/, { timeout: 10000 });
    const protocol = await page.locator('#protocoloText').innerText();
    assert.match(protocol, /^DEL-/);
    console.log(`OK aluno: solicitação criada com protocolo ${protocol}`);

    await page.goto(base + '/consultar-protocolo');
    const lookup = page.locator('form').first();
    await lookup.locator('input[name="protocolo"]').fill(protocol);
    await lookup.locator('input[name="email"]').fill('teste.e2e@example.test');
    await lookup.getByRole('button', { name: 'Consultar' }).click();
    await page.waitForURL(new RegExp(`/status/${protocol}$`), { timeout: 10000 });
    assert.equal(await page.locator('#protocoloText').innerText(), protocol);
    console.log('OK aluno: consulta por protocolo e email');

    assert.deepEqual(errors, [], `Erros JavaScript: ${errors.join('; ')}`);
    console.log('OK: sem exceções JavaScript nas telas visitadas');
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
