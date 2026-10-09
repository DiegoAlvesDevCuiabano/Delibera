const assert = require('node:assert/strict');
const { chromium } = require('playwright');

const base = process.env.DELIBERA_BASE_URL || 'http://127.0.0.1:18441';
assert.ok(['127.0.0.1', 'localhost'].includes(new URL(base).hostname),
  'O teste com envio ficticio so pode rodar contra localhost.');

(async () => {
  const browser = await chromium.launch({ headless: true,
    ...(process.env.PLAYWRIGHT_CHROMIUM ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM } : {}) });
  try {
    const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    const response = await page.goto(base + '/suporte', { waitUntil: 'domcontentloaded' });
    assert.equal(response.status(), 200);
    await page.getByRole('heading', { name: 'Como podemos ajudar?' }).waitFor();
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 2), true);
    const faq = page.getByRole('button', { name: 'Como encaminho uma solicitação para um professor?' });
    await faq.click();
    assert.equal(await faq.getAttribute('aria-expanded'), 'true');
    await page.locator('input[name="nome"]').fill('Teste de ajuda');
    await page.locator('input[name="email"]').fill('ajuda@example.test');
    await page.locator('select[name="assunto"]').selectOption('Dúvida');
    await page.locator('textarea[name="mensagem"]').fill('Mensagem fictícia de homologação local.');
    await page.getByRole('button', { name: 'Enviar mensagem' }).click();
    await page.getByText('Recebemos sua mensagem!').waitFor({ timeout: 10000 });
    assert.deepEqual(errors, []);
    console.log('OK suporte: FAQ, tela móvel, formulário, resposta HTMX e JavaScript');
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
