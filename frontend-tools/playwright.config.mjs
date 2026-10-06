/** Browser tests run serially against the disposable demo ledger. */
import {defineConfig} from '@playwright/test';
export default defineConfig({
  testDir:'./tests',testMatch:'*.spec.mjs',workers:1,fullyParallel:false,timeout:60000,
  use:{baseURL:process.env.BASE_URL||'http://localhost:18080',viewport:{width:1440,height:1000},locale:'en-IN',timezoneId:'Asia/Kolkata',trace:'retain-on-failure'},
  reporter:[['list'],['html',{open:'never'}]]
});
