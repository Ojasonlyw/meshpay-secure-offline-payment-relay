/** Validate every dashboard field consumed by chart, activity and security views. */
import {isText,isNumber,isCount,validatePaymentSummary} from './validators.js';
import {ApiError} from './client.js';
const optionalText=value=>value==null||isText(value);
const date=value=>isText(value)&&Number.isFinite(Date.parse(value));
const finite=value=>typeof value==='number'&&Number.isFinite(value);
function checked(data,valid,label){if(!valid)throw new ApiError('The server returned invalid '+label+'.',{errorCode:'INVALID_RESPONSE'});return data;}
function points(data){return Array.isArray(data?.points)&&data.points.every(p=>p&&/^\d{4}-\d{2}-\d{2}$/.test(p.bucket)&&date(p.bucket)&&['credits','debits','netMovement','settledAmount'].every(key=>isNumber(p[key])));}
/** @param {*} data @returns {object} */
export function validateSummary(data){
  validatePaymentSummary(data);
  return checked(data,isNumber(data.totalBalance)&&['activeDevices','bridgeDevices','activeConnections','idempotencyCacheSize','totalTransactions','settledPayments'].every(key=>isCount(data[key])),'summary');
}
/** @param {*} data @returns {object} */
export function validateCashflow(data){return checked(data,points(data)&&optionalText(data.accountVpa)&&['netMovement','totalCreditVolume','totalDebitVolume'].every(key=>isNumber(data[key])),'cashflow');}
/** @param {*} data @returns {object} */
export function validateVolume(data){return checked(data,points(data)&&isNumber(data.settledAmount),'transaction volume');}
/** @param {*} data @returns {object} */
export function validateActivity(data){return checked(data,Array.isArray(data?.items)&&data.items.every(item=>item&&['type','title','description'].every(key=>isText(item[key]))&&date(item.occurredAt)&&optionalText(item.referenceId)),'activity');}
/** @param {*} data @returns {object} */
export function validateNetwork(data){return checked(data,data&&['totalDevices','activeConnections','packetsRouted','trustedDevices','revokedDevices'].every(key=>isCount(data[key]))&&finite(data.averageHopCount)&&(data.latestRouteTimestamp==null||date(data.latestRouteTimestamp))&&optionalText(data.mostActiveBridgeDevice),'network statistics');}
/** @param {*} data @returns {object} */
export function validateSecurity(data){return checked(data,isCount(data?.failedSignatureVerification)&&Array.isArray(data.items)&&data.items.every(item=>item&&isText(item.eventType)&&isText(item.message)&&date(item.occurredAt)&&optionalText(item.deviceId)&&optionalText(item.paymentId)),'security events');}
/** @param {*} data @returns {Array} */
export function validateRoutes(data){return checked(data,Array.isArray(data)&&data.every(item=>item&&['packetId','sourceDeviceId','destinationDeviceId'].every(key=>isText(item[key]))&&isCount(item.hopNumber)&&isCount(item.ttlAfterHop)&&date(item.receivedAt)&&optionalText(item.paymentId)),'routes');}
