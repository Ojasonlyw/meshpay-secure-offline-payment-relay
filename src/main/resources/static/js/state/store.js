/** Owns application data, selections, loading/errors and mutation status. */
const initial = {accounts:[],payments:[],transactions:[],activity:[],cashflow:null,volume:null,network:null,security:null,routes:[],selectedAccount:null,paymentStatus:'',activityType:'',range:'all',selectedDevice:null,mesh:null,summary:null,transactionsLoaded:false,mutationPending:false,sections:{}};
/** @param {object} seed Initial snapshot. @returns {{get:Function,set:Function,subscribe:Function}} */
export function createStore(seed={}) {
  let value=Object.freeze({...initial,...seed});
  const listeners=new Set();
  return {
    get:()=>value,
    set(patch){const previous=value;value=Object.freeze({...value,...patch});for(const listener of listeners)listener(value,previous);return value;},
    subscribe(listener){listeners.add(listener);return ()=>listeners.delete(listener);}
  };
}
const store=createStore();
/** @returns {object} Current application snapshot. */
export const getState=()=>store.get();
/** @param {object} patch Changed top-level fields. @returns {object} Next snapshot. */
export const setState=patch=>store.set(patch);
/** @param {Function} listener Snapshot observer. @returns {Function} Unsubscribe. */
export const subscribe=listener=>store.subscribe(listener);
