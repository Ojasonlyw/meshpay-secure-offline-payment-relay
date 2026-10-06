/** Run lint from the repository root while keeping dependencies isolated. */
import {ESLint} from 'eslint';
import stylelint from 'stylelint';
import path from 'node:path';
const root=path.resolve('..');
const eslint=new ESLint({cwd:root,overrideConfigFile:path.resolve('eslint.config.mjs')});
const results=await eslint.lintFiles(['src/main/resources/static/js/**/*.js','frontend-tools/*.mjs','frontend-tools/tests/*.mjs']);
console.log(await (await eslint.loadFormatter('stylish')).format(results));
const css=await stylelint.lint({files:path.join(root,'src/main/resources/static/css/**/*.css').replaceAll('\\','/'),configFile:path.resolve('.stylelintrc.json'),formatter:'string'});
console.log(css.report);
if(results.some(result=>result.errorCount)||css.errored)process.exitCode=1;
