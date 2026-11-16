// Fails the build if the app page has a JavaScript syntax error. Usage: node tools/check_page.js <page.html>
const fs = require('fs'), vm = require('vm');
const html = fs.readFileSync(process.argv[2], 'utf8');
const scripts = [...html.matchAll(/<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/g)].map(m => m[1]);
if (!scripts.length) { console.error('no inline script found'); process.exit(1); }
scripts.forEach((code, i) => { new vm.Script(code, { filename: 'inline-script-' + i + '.js' }); });
for (const id of ['s-home', 's-cal', 's-map', 's-saved', 's-me', 'detail', 'sheet'])
  if (!html.includes('id="' + id + '"')) { console.error('missing #' + id); process.exit(1); }
console.log('page OK:', scripts.length, 'script block(s)');
