/** Resource scheduler: serialized reads, cancellation and bounded failure backoff. */
import {MAX_BACKOFF} from '../config.js';
/** @param {number} interval @param {number} failures @returns {number} */
export const backoff=(interval,failures)=>Math.min(MAX_BACKOFF,interval*2**failures);
/** @param {Array<object>} resources @param {object} clock Injectable timers. @returns {object} */
export function createPoller(resources,clock={setTimeout:(fn,ms)=>setTimeout(fn,ms),clearTimeout:id=>clearTimeout(id)}){
  const entries=new Map(resources.map(resource=>[resource.name,{resource,timer:null,flight:null,failures:0}]));
  let paused=true;
  async function refresh(name){
    const entry=entries.get(name);if(!entry)throw new Error('Unknown polling resource: '+name);
    const key=entry.resource.key?.()??name;
    if(entry.flight){if(entry.flight.key===key)return entry.flight.promise;entry.flight.controller.abort();await entry.flight.promise;}
    clock.clearTimeout(entry.timer);
    const controller=new AbortController();const flight={key,controller,promise:null};
    entry.flight=flight;
    flight.promise=(async()=>{
      try{
        const data=await entry.resource.read(controller.signal);
        if(controller.signal.aborted||key!==(entry.resource.key?.()??name))return;
        entry.resource.onData(data);entry.failures=0;
      }catch(error){if(!controller.signal.aborted){entry.failures++;entry.resource.onError(error);}}
      finally{
        if(entry.flight===flight){entry.flight=null;entry.resource.onSettled?.();if(!paused)entry.timer=clock.setTimeout(()=>refresh(name),backoff(entry.resource.interval,entry.failures));}
      }
    })();
    return flight.promise;
  }
  function pause(){paused=true;for(const entry of entries.values()){clock.clearTimeout(entry.timer);entry.flight?.controller.abort();}}
  async function drain(){await Promise.all([...entries.values()].map(entry=>entry.flight?.promise));}
  function resume(){paused=false;}
  return {refresh,pause,resume,drain,refreshAll:()=>Promise.all([...entries.keys()].map(refresh))};
}
