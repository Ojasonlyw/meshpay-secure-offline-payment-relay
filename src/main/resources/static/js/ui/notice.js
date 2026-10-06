/** Queued notices reuse the existing banner and live region. */
import {NOTICE_DURATION} from '../config.js';
const queue=[];let active=null,timer;
function display(){
  clearTimeout(timer);active=queue.shift()||null;
  const element=document.getElementById('notice');
  element.replaceChildren();element.hidden=!active;if(!active)return;
  element.classList.toggle('failure',active.failed);
  element.append(document.createTextNode(active.message));
  if(active.traceId){
    const trace=document.createElement('small');trace.className='muted';trace.textContent=` Trace: ${active.traceId}`;
    const copy=document.createElement('button');copy.type='button';copy.className='text-button';copy.textContent='Copy trace';
    copy.addEventListener('click',async()=>{
      try{await navigator.clipboard.writeText(active.traceId);}catch{const range=document.createRange();range.selectNodeContents(trace);const selection=window.getSelection();selection.removeAllRanges();selection.addRange(range);}
    });element.append(trace,copy);
  }
  if(active.failed){const dismiss=document.createElement('button');dismiss.type='button';dismiss.className='text-button';dismiss.textContent='Dismiss';dismiss.addEventListener('click',display);element.append(dismiss);}
  else timer=setTimeout(display,NOTICE_DURATION);
}
/** @param {string} message Safe text. @param {boolean} failed Persist until dismissed. @param {string|null} traceId Backend trace. @returns {void} */
export function notice(message,failed=false,traceId=null){
  if([active,...queue].some(item=>item&&item.message===message&&item.traceId===traceId))return;
  queue.push({message,failed,traceId});if(!active)display();
}
