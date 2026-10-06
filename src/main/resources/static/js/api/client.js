/** Fetch JSON with timeouts, cancellation and safe backend error metadata. */
import {REQUEST_TIMEOUT} from '../config.js';
export class ApiError extends Error {
  /** @param {string} message Safe text. @param {object} details Error metadata. */
  constructor(message,{status=0,errorCode='NETWORK_ERROR',traceId=null}={}){super(message);this.name='ApiError';this.status=status;this.errorCode=errorCode;this.traceId=traceId;}
}
/** @param {Response} response HTTP response. @param {*} body Parsed body. @returns {ApiError} */
export function responseError(response,body) {
  return new ApiError(typeof body?.message==='string'?body.message:'Request could not be processed.',{
    status:response.status,errorCode:typeof body?.errorCode==='string'?body.errorCode:'HTTP_ERROR',
    traceId:typeof body?.traceId==='string'?body.traceId:response.headers.get('X-Trace-Id')
  });
}
/** @param {string} path Existing API route. @param {object} options Fetch options. @returns {Promise<*>} */
export async function request(path,options={}) {
  const controller=new AbortController();let timedOut=false;
  const cancel=()=>controller.abort();
  options.signal?.addEventListener('abort',cancel,{once:true});
  if(options.signal?.aborted)cancel();
  const timer=setTimeout(()=>{timedOut=true;controller.abort();},REQUEST_TIMEOUT);
  try{
    const response=await fetch(path,{...options,signal:controller.signal,cache:'no-store'});
    let body;
    try{body=await response.json();}catch{
      if(!response.ok)throw responseError(response,null);
      throw new ApiError('The server returned an unreadable response.',{status:response.status,errorCode:'INVALID_RESPONSE',traceId:response.headers.get('X-Trace-Id')});
    }
    if(!response.ok)throw responseError(response,body);
    return body;
  }catch(error){
    if(error instanceof ApiError)throw error;
    if(controller.signal.aborted)throw new ApiError(timedOut?'The request timed out.':'Request canceled.',{errorCode:timedOut?'TIMEOUT':'ABORTED'});
    throw new ApiError('Unable to reach the demo server.');
  }finally{clearTimeout(timer);options.signal?.removeEventListener('abort',cancel);}
}
