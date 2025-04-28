const fs = require('fs');
const compiled = new WebAssembly.Module(fs.readFileSync('output.wasm'));
const imports = {};
const instance = new WebAssembly.Instance(compiled, imports);

console.log(instance.exports.main());
