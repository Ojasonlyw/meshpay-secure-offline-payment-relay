/** Shared deterministic Chromium capture settings and fixture routing. */
import { chromium } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import {referenceScroll} from './scrollOffsets.mjs';
export const baseURL = process.env.BASE_URL || 'http://localhost:18080';
export const widths = [1440, 1280, 1024, 768, 430, 360];
export const paths = ['/api/payments', '/api/dashboard/summary', '/api/mesh/state', '/api/accounts', '/api/transactions', '/api/dashboard/cashflow', '/api/dashboard/activity', '/api/dashboard/network-stats', '/api/dashboard/transaction-volume', '/api/dashboard/security-events', '/api/mesh/routes'];
export async function waitForApp() {
  for(let attempt=0;attempt<60;attempt++) {
    try { const response=await fetch(baseURL); if(response.ok) return; } catch { /* Startup. */ }
    await new Promise(resolve=>setTimeout(resolve,1000));
  }
  throw new Error('Application did not become ready');
}
export async function snapshotAPI() {
  const pairs = await Promise.all(paths.map(async path => {
    const response = await fetch(baseURL + path);
    if (!response.ok) throw new Error(`${path}: ${response.status}`);
    return [path, await response.json()];
  }));
  const data = Object.fromEntries(pairs);
  for (const account of data['/api/accounts']) {
    const path = '/api/dashboard/cashflow?accountVpa=' + encodeURIComponent(account.vpa);
    const response = await fetch(baseURL + path);
    if (!response.ok) throw new Error(path);
    data[path] = await response.json();
  }
  return data;
}
export async function fixtures(name) {
  return JSON.parse(await readFile(`baseline/${name}.json`, 'utf8'));
}
export async function openPage(browser, width, data) {
  const context = await browser.newContext({ viewport: { width, height: 1000 }, deviceScaleFactor: 1, locale: 'en-IN', timezoneId: 'Asia/Kolkata' });
  const page = await context.newPage();
  page.on('pageerror',error=>console.error('Browser error:',error.stack));
  await page.addInitScript(() => {
    const OriginalDate = Date;
    window.Date = class extends OriginalDate {
      constructor(...args) { super(...(args.length ? args : ['2026-10-06T10:00:00Z'])); }
      static now() { return new OriginalDate('2026-10-06T10:00:00Z').getTime(); }
    };
  });
  await page.route('**/api/**', route => {
    const url = new URL(route.request().url());
    if(!url.pathname.startsWith('/api/'))return route.continue();
    const payload = data[url.pathname + url.search] ?? data[url.pathname];
    return route.fulfill({ status: payload === undefined ? 404 : 200, contentType: 'application/json', body: JSON.stringify(payload ?? {}) });
  });
  await page.goto(baseURL);
  try { await page.waitForFunction(() => document.getElementById('connection-text').textContent === 'Live connection'); }
  catch(error) { console.error(await page.locator('.section-error:not([hidden])').allTextContents()); throw error; }
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(750);
  await page.mouse.move(0, 0);
  return {context, page};
}
export async function styles(page) {
  return page.evaluate(() => {
    const properties = ['color','background','font','letter-spacing','margin','padding','border','border-radius','box-shadow','backdrop-filter','display','position','width','height','gap','grid-template-columns','transform','outline','opacity'];
    const key = el => !el ? 'document' : el.id ? '#' + el.id : key(el.parentElement) + '/' + el.tagName.toLowerCase() + ':nth-child(' + ([...(el.parentElement?.children || [el])].indexOf(el)+1) + ')';
    return Object.fromEntries([...document.querySelectorAll('[id], [class]')].map(el => [key(el), Object.fromEntries(properties.map(p => [p, getComputedStyle(el).getPropertyValue(p)]))]));
  });
}
export async function capture(directory) {
  await waitForApp();
  await mkdir(directory, {recursive:true});
  const browser = await chromium.launch();
  const computed = {}, accessibility = {};
  try {
    for (const width of widths) {
      for (const state of ['normal','reset','route','invalid', ...(width < 768 ? ['drawer'] : []), 'demo']) {
        const {context,page} = await openPage(browser, width, await fixtures(state === 'demo' ? 'demo' : 'normal'));
        if(state === 'reset') await page.locator('#reset').click();
        if(state === 'route') await page.locator('#add-route').click();
        if(state === 'drawer') await page.locator('#menu-toggle').click();
        if(state === 'invalid') {
          const scroll=directory==='actual'?await referenceScroll(page,width):null;
          await page.locator('#amount').fill('0');
          await page.locator('#payment-form button[type=submit]').click();
          if(!await page.locator('#amount').evaluate(el=>!el.validity.valid)) throw new Error('Invalid amount accepted');
          if(scroll!==null){await page.waitForTimeout(600);await page.evaluate(scroll=>window.scrollTo({top:scroll,behavior:'instant'}),scroll);}
        }
        await page.waitForTimeout(300);
        await page.mouse.move(0, 0);
        const name = `${width}-${state}`;
        await page.screenshot({path:`${directory}/${name}.png`,fullPage:true, animations:'disabled', caret:'hide'});
        computed[name] = await styles(page);
        if(state === 'normal' || state === 'drawer' || state === 'reset' || state === 'route') {
          const result = await new AxeBuilder({page}).analyze();
          accessibility[name] = result.violations.map(v=>({id:v.id, impact:v.impact, count:v.nodes.length}));
        }
        await context.close();
        console.log('Captured',name);
      }
    }
    await writeFile(`${directory}/computed-styles.json`,JSON.stringify(computed));
    await writeFile(`${directory}/accessibility.json`,JSON.stringify(accessibility,null,2));
  } finally { await browser.close(); }
}
