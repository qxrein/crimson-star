const fs = require('fs');

async function run() {
  try {
    const wasm = await fs.promises.readFile('output.wasm');
    const module = await WebAssembly.compile(wasm);
    const instance = await WebAssembly.instantiate(module);
    console.log('WASM exports:', Object.keys(instance.exports));
    if (instance.exports.main) {
      console.log('Result:', instance.exports.main());
    }
  } catch (err) {
    console.error('Error:', err.message);
    console.error('Stack:', err.stack);
  }
}

run();
