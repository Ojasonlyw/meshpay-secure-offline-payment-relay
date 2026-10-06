/** Development lint rules for browser modules and test tools. */
import js from '@eslint/js';
import globals from 'globals';
import noUnsanitized from 'eslint-plugin-no-unsanitized';
export default [
  {ignores:['node_modules/**','baseline/**','cleanup/**','actual/**','measurements/**','playwright-report/**','test-results/**']},
  {files:['**/*.js','**/*.mjs','../src/main/resources/static/js/**/*.js'],...js.configs.recommended,
    languageOptions:{ecmaVersion:'latest',sourceType:'module',globals:{...globals.browser,...globals.node}},
    plugins:{'no-unsanitized':noUnsanitized},
    rules:{'no-unsanitized/method':'error','no-unsanitized/property':'error','no-unused-vars':['error',{argsIgnorePattern:'^_',varsIgnorePattern:'^_'}]}}
];
