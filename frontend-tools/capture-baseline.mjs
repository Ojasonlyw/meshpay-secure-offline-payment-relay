/** Capture immutable original/cleanup references, refusing accidental replacement. */
import { mkdir, writeFile, access } from 'node:fs/promises';
import { capture, snapshotAPI, baseURL } from './browser.mjs';
const directory = process.argv[2] || 'baseline';
if(!['baseline','cleanup'].includes(directory)) throw new Error('Expected baseline or cleanup');
try { await access(`${directory}/computed-styles.json`); throw new Error('Reference already exists: do not overwrite'); } catch(error) { if(error.code !== 'ENOENT') throw error; }
let hasFixtures = false;
try { await access('baseline/normal.json'); hasFixtures = true; } catch { /* First capture. */ }
if(directory === 'baseline' && !hasFixtures) {
  await mkdir('baseline',{recursive:true});
  await writeFile('baseline/normal.json',JSON.stringify(await snapshotAPI(),null,2));
  async function post(path,body) {
    const response = await fetch(baseURL+path,{method:'POST',headers:{'Content-Type':'application/json'},...(body?{body:JSON.stringify(body)}:{})});
    if(!response.ok) throw new Error(`${path}: ${response.status}`);
    return response.json();
  }
  await post('/api/demo/send',{senderVpa:'alice@demo',receiverVpa:'bob@demo',amount:1,pin:'',ttl:5});
  for(let round=0;round<4;round++) await post('/api/mesh/gossip');
  const flush = await post('/api/mesh/flush');
  if(!flush.results.some(result=>result.outcome==='SETTLED')) throw new Error('Baseline flow did not settle');
  await writeFile('baseline/demo.json',JSON.stringify(await snapshotAPI(),null,2));
}
await capture(directory);
