/** One-time mechanical extraction; prints an apply_patch, never writes application files. */
import {readFile} from 'node:fs/promises';
import path from 'node:path';
import {parse} from 'acorn';
import {analyze} from 'eslint-scope';
import postcss from 'postcss';
import * as parse5 from 'parse5';
const root=path.resolve('..').replaceAll('\\','/');
const files=new Map();
function add(name,text){files.set(name,text.replaceAll('\r\n','\n'));}
function walk(node,visit,parent){if(!node||typeof node!=='object')return; if(node.type)visit(node,parent);for(const [key,value] of Object.entries(node)){if(['start','end','range','loc'].includes(key))continue;if(Array.isArray(value))value.forEach(child=>walk(child,visit,node));else if(value&&typeof value==='object')walk(value,visit,node);}}
const stage=process.argv[2];
if(stage==='js') {
  const source=(await readFile('../src/main/resources/static/js/dashboard.js','utf8')).replaceAll('\r\n','\n');
  const tree=parse(source,{ecmaVersion:'latest',ranges:true});
  const body=tree.body[0].expression.callee.body.body;
  const nodes=Object.fromEntries(body.filter(n=>n.type==='FunctionDeclaration').map(n=>[n.id.name,n]));
  for(const n of body.filter(n=>n.type==='VariableDeclaration'))for(const d of n.declarations)nodes[d.id.name]=n;
  const props=['accounts','payments','transactions','activity','cashflow','volume','network','security','routes','selectedAccount','paymentStatus','activityType','range','selectedDevice','mesh'];
  function convert(text) {
    const ast=parse(text,{ecmaVersion:'latest',ranges:true}); const edits=[];
    walk(ast,n=>{
      if(n.type==='AssignmentExpression'&&n.left.type==='MemberExpression'&&n.left.object.name==='state'&&props.includes(n.left.property.name)){
        const value=text.slice(n.right.start,n.right.end).replace(new RegExp('\\bstate\\.('+props.join('|')+')\\b','g'),'getState().$1');
        edits.push({start:n.start,end:n.end,value:`setState({${n.left.property.name}: ${value}})`});
      } else if(n.type==='MemberExpression'&&n.object.name==='state'&&props.includes(n.property.name)) edits.push({start:n.start,end:n.end,value:`getState().${n.property.name}`});
    });
    const selected=edits.filter(e=>!edits.some(other=>other!==e&&other.start<=e.start&&other.end>=e.end));
    for(const e of selected.sort((a,b)=>b.start-a.start))text=text.slice(0,e.start)+e.value+text.slice(e.end);
    return text.replace(/\btransactions\.filter/g,'getState().transactions.filter').replace(/\btransactions\.length/g,'getState().transactions.length').replace(/!transactionsLoaded\b/g,'!getState().transactionsLoaded').replace(/\bmutationPending\b/g,'getState().mutationPending');
  }
  const groups={
    'ui/dom.js':['$','node','icon','renderIcons','emptyRow'],
    'format/money.js':['currency','money','compactMoney'],
    'format/dates.js':['dateTime','chartDate','activityTime'],
    'format/status.js':['statusStyles','statusLabels'],
    'api/validators.js':['isText','isNumber','isCount','validateMesh','validateAccounts','validateTransactions','validatePayments','validatePaymentSummary'],
    'ui/charts.js':['chartPoint','linePath'],
    'views/payments.js':['renderPayments','renderPaymentSummary','focusPayment'],
    'views/mesh.js':['renderMesh','renderNetwork','renderRoutes'],
    'views/accounts.js':['renderAccounts'],
    'views/overview.js':['renderSummary'],
    'views/analytics.js':['renderCashflow','renderVolume'],
    'views/activity.js':['focusReference','renderActivity'],
    'views/security.js':['renderSecurity'],
    'views/transactions.js':['renderTransactions'],
    'views/paymentForm.js':[],
    'ui/dialogs.js':[],
    'services/search.js':[],
    'ui/navigation.js':['setDrawer','updateNavigation','resizeNavigation','mobile']
  };
  const definitions=Object.fromEntries(Object.entries(groups).flatMap(([file,names])=>names.map(name=>[name,file])));
  Object.assign(definitions,{getState:'state/store.js',setState:'state/store.js',request:'api/client.js',notice:'ui/notice.js',mutate:'services/mutations.js',refresh:'services/runtime.js',refreshSection:'services/runtime.js'});
  const events=Object.fromEntries(Object.keys(groups).map(file=>[file,[]]));
  for(const n of body.filter(n=>n.type==='ExpressionStatement')) {
    const text=source.slice(n.start,n.end); if(!text.includes('addEventListener')&&!text.includes('link.title'))continue;
    let file;
    const id=text.match(/^\$\("([^"]+)"\)/)?.[1];
    if(['applications-button','messages-button','refresh'].includes(id)||text.includes('visibilitychange'))continue;
    if(['payment-form','amount'].includes(id))file='views/paymentForm.js';
    else if(['gossip','flush','devices'].includes(id))file='views/mesh.js';
    else if(['tx-search','tx-status','tx-table'].includes(id))file='views/transactions.js';
    else if(['payment-counts','payment-filter-clear','payments-table'].includes(id))file='views/payments.js';
    else if(id==='accounts-table')file='views/accounts.js';
    else if(id==='activity-type')file='views/activity.js';
    else if(id==='notifications-button')file='views/security.js';
    else if(id==='command-search'||text.includes('event.ctrlKey'))file='services/search.js';
    else if(id?.startsWith('route-')||id?.startsWith('reset')||id==='add-route')file='ui/dialogs.js';
    else if(text.includes('[data-range]'))file='views/analytics.js';
    else file='ui/navigation.js';
    events[file].push(convert(text));
  }
  events['ui/navigation.js'].push('resizeNavigation();','updateNavigation();');
  for(const [file,names] of Object.entries(groups)) {
    let code=names.map(name=>{
      const n=nodes[name];let text=convert(source.slice(n.start,n.end));
      if(n.type==='FunctionDeclaration')return `/** ${name} owns its existing dashboard behavior.\n${n.params.filter(p=>p.type==='Identifier').map(p=>` * @param {*} ${p.name}`).join('\n')}\n * @returns {*}\n */\nexport ${text}`;
      return `export ${text}`;
    }).join('\n\n');
    if(events[file].length) code+='\n\n/** Wire this module once after the document is ready. @returns {void} */\nexport function init() {\n'+events[file].join('\n')+'\n}\n';
    const ast=parse(code,{ecmaVersion:'latest',sourceType:'module',ranges:true});
    const scope=analyze(ast,{ecmaVersion:2022,sourceType:'module'});
    const imported=new Map();
    for(const ref of scope.globalScope.through){const name=ref.identifier.name;const owner=definitions[name];if(owner&&owner!==file){if(!imported.has(owner))imported.set(owner,new Set());imported.get(owner).add(name);}}
    const imports=[...imported].map(([owner,names])=>{let relative=path.posix.relative(path.posix.dirname(file),owner);if(!relative.startsWith('.'))relative='./'+relative;return `import {${[...names].join(', ')}} from '${relative}';`;}).join('\n');
    add('src/main/resources/static/js/'+file,`/** Owns ${file.replace('.js','').replace('/',' ')}. */\n${imports}\n\n${code}\n`);
  }
}
if(stage==='css'){
  let source=(await readFile('../src/main/resources/static/css/dashboard.css','utf8')).replaceAll('\r\n','\n');
  const tokens=[];
  for(const alpha of [2,3,4,5,7,8,9,11,12,13,14,18,20,22,24,30]){
    const literal=`rgb(255 255 255 / ${alpha}%)`;
    if(source.split(literal).length>2){tokens.push(`  --white-${alpha}: ${literal};`);source=source.replaceAll(literal,`var(--white-${alpha})`);}
  }
  source=source.replace('  --bg: #060606;',tokens.join('\n')+'\n  --bg: #060606;');
  source=source.replace('padding: 24px 12px !important;','padding: 24px 12px;');
  const ast=postcss.parse(source);let start=0,buffer='',parts=[];
  for(const node of ast.nodes){const end=node.source.end.offset;const text=source.slice(start,end);start=end;if((buffer+text).split('\n').length>250&&buffer){parts.push(buffer);buffer='';}buffer+=text;}
  if(buffer)parts.push(buffer+source.slice(start));
  const names=parts.map((_,i)=>`${i===0?'base':i===1?'layout':i===parts.length-1?'responsive':'components'}/${String(i+1).padStart(2,'0')}-dashboard.css`);
  parts.forEach((text,i)=>add('src/main/resources/static/css/'+names[i],`/* Ordered extraction ${i+1}; preserve source-order cascade. */\n${text.trim()}\n`));
  add('src/main/resources/static/css/dashboard.css','/* Styles are linked as ordered partials by dashboard.html; no imports or build step. */\n');
  let html=await readFile('../src/main/resources/templates/dashboard.html','utf8');
  const startLink=html.indexOf('    <link');const endLink=html.indexOf('    />',startLink)+6;
  html=html.slice(0,startLink)+names.map(name=>`    <link rel="stylesheet" href="/css/${name}" th:href="@{/css/${name}}" />`).join('\n')+html.slice(endLink);
  add('src/main/resources/templates/dashboard.html',html);
}
if(stage==='html'){
  let source=(await readFile('../src/main/resources/templates/dashboard.html','utf8')).replaceAll('\r\n','\n');
  const tree=parse5.parse(source,{sourceCodeLocationInfo:true});const selected=[];
  function htmlWalk(node){if(node.tagName){const attrs=Object.fromEntries(node.attrs.map(a=>[a.name,a.value]));let name;if(attrs.id==='sidebar')name='sidebar';if(node.tagName==='header')name='topbar';if(attrs.id==='overview')name='overview';if(attrs.id==='analytics'||attrs.class==='bottom-grid')name='analytics';if(attrs.id==='payment-panel')name='payment-form';if(attrs.id==='mesh')name='mesh';if(['accounts','transactions','activity'].includes(attrs.id)||attrs['aria-labelledby']==='payments-heading')name='tables';if(node.tagName==='dialog')name='dialogs';if(name){selected.push({name,node});return;}}for(const child of node.childNodes||[])htmlWalk(child);}
  htmlWalk(tree);const grouped=new Map();for(const item of selected){if(!grouped.has(item.name))grouped.set(item.name,[]);grouped.get(item.name).push(item.node);}
  const edits=[];
  for(const [name,nodes] of grouped){add(`src/main/resources/templates/fragments/${name}.html`,`<th:block xmlns:th="http://www.thymeleaf.org" th:fragment="${name}">\n${nodes.map(n=>source.slice(n.sourceCodeLocation.startOffset,n.sourceCodeLocation.endOffset)).join('\n')}\n</th:block>\n`);nodes.forEach((n,i)=>edits.push({start:n.sourceCodeLocation.startOffset,end:n.sourceCodeLocation.endOffset,text:i===0?`<th:block th:replace="~{fragments/${name} :: ${name}}"></th:block>`:''}));}
  for(const e of edits.sort((a,b)=>b.start-a.start))source=source.slice(0,e.start)+e.text+source.slice(e.end);
  source=source.replace('<meta name="color-scheme" content="dark" />','<meta name="color-scheme" content="dark" />\n    <meta name="description" content="MeshPay simulated offline mesh payment dashboard and demo ledger." />\n    <meta name="theme-color" content="#060606" />\n    <link rel="icon" href="/favicon.svg" th:href="@{/favicon.svg}" type="image/svg+xml" />');
  add('src/main/resources/templates/dashboard.html',source);
  add('src/main/resources/static/favicon.svg','<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"><path fill="none" stroke="#ff421e" stroke-width="4" stroke-linecap="round" d="M16 4v24M6 10l20 12M6 22l20-12"/></svg>\n');
}
let patch='*** Begin Patch\n';
for(const [name,text] of files){let before;try{before=(await readFile(root+'/'+name,'utf8')).replaceAll('\r\n','\n');}catch{}if(before!==undefined)patch+=`*** Update File: ${root}/${name}\n@@\n`+before.trimEnd().split('\n').map(l=>'-'+l).join('\n')+'\n'+text.trimEnd().split('\n').map(l=>'+'+l).join('\n')+'\n';else patch+=`*** Add File: ${root}/${name}\n`+text.trimEnd().split('\n').map(l=>'+'+l).join('\n')+'\n';}
process.stdout.write(patch+'*** End Patch\n');
