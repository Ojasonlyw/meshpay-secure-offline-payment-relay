/** Accessibility measurement with the same containerized Chromium and fixtures. */
import {chromium} from '@playwright/test';
import lighthouse from 'lighthouse';
import {writeFile,mkdir} from 'node:fs/promises';
import {baseURL,waitForApp} from './browser.mjs';
await waitForApp();
import {spawn} from 'node:child_process';
const child=spawn(chromium.executablePath(),['--headless','--no-sandbox','--remote-debugging-port=9222']);
try{
  for(let i=0;i<50;i++){
    try{await fetch('http://localhost:9222/json/version');break;}catch{await new Promise(resolve=>setTimeout(resolve,100));}
  }
  const result=await lighthouse(baseURL,{port:9222,onlyCategories:['accessibility'],output:'json',logLevel:'error'});
  await mkdir('measurements',{recursive:true});
  const score=result.lhr.categories.accessibility.score*100;
  await writeFile(`measurements/${process.argv[2]||'after'}-lighthouse.json`,JSON.stringify({score,audits:result.lhr.audits},null,2));
  console.log('Lighthouse accessibility:',score);
}finally{child.kill();}
