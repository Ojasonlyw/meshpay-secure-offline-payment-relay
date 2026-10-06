/** Measure real idle dashboard GET traffic over a sixty-second observation. */
import {chromium} from '@playwright/test';
import {writeFile,mkdir} from 'node:fs/promises';
import {baseURL,waitForApp} from './browser.mjs';
await waitForApp();
const browser=await chromium.launch();
try {
  const page=await browser.newPage();
  await page.goto(baseURL);
  await page.locator('#connection-text').filter({hasText:'Live connection'}).waitFor();
  const requests=[];
  page.on('request',request=>{
    if(request.method()==='GET'&&new URL(request.url()).pathname.startsWith('/api/')) requests.push(new URL(request.url()).pathname);
  });
  await page.waitForTimeout(60000);
  const result={seconds:60,requests:requests.length,byEndpoint:Object.fromEntries([...new Set(requests)].map(path=>[path,requests.filter(value=>value===path).length]))};
  await mkdir('measurements',{recursive:true});
  await writeFile(`measurements/${process.argv[2]||'after'}-network.json`,JSON.stringify(result,null,2));
  console.log(JSON.stringify(result));
}finally{await browser.close();}
