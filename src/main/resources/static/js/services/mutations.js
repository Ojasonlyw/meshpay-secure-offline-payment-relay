/** Serialize user writes; reconcile ambiguous results without replaying writes. */
import {post} from '../api/endpoints.js';
import {getState,setState} from '../state/store.js';
import {notice} from '../ui/notice.js';
import {pause,drain,refresh,resume} from './runtime.js';
/** @param {string} path Existing POST route. @param {object|null} body Request payload. @param {Function} onSuccess Validated result handler. @returns {Promise<void>} */
export async function mutate(path,body,onSuccess){
  if(getState().mutationPending)return;
  setState({mutationPending:true});pause();
  const buttons=[...document.querySelectorAll('[data-mutation]'),document.getElementById('refresh')];
  buttons.forEach(button=>button.disabled=true);
  try{await drain();onSuccess(await post(path,body));}
  catch(error){notice(error.message+' Check the refreshed state before trying again.',true,error.traceId);}
  finally{
    setState({mutationPending:false});buttons.forEach(button=>button.disabled=false);
    if(!document.hidden)resume();await refresh();
  }
}
