const express = require('express');
const puppeteer = require('puppeteer');
const app = express();

app.get('/screenshot', async (req, res) => {
  const url = req.query.url || 'https://example.com';
  const browser = await puppeteer.launch({
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });
  const page = await browser.newPage();
  await page.goto(url);
  const screenshot = await page.screenshot();
  await browser.close();
  res.type('image/png').send(screenshot);
});

app.listen(3000, () => console.log('Puppeteer service running on port 3000'));
