/** Quick browser startup diagnostic, also useful when recovering failed tests. */
import {chromium} from '@playwright/test';
import {baseURL} from './browser.mjs';
const browser=await chromium.launch();
try{
  const page=await browser.newPage();
  page.on('pageerror',error=>console.error('BROWSER',error.stack));
  page.on('requestfailed',request=>console.error('REQUEST',request.url(),request.failure()));
  await page.goto(baseURL);await page.waitForTimeout(2000);
  console.log(await page.locator('#connection-text').textContent());
  console.log(await page.locator('.section-error:not([hidden])').allTextContents());
}finally{await browser.close();}
