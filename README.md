# crimson-star
tiny Starlark subset compiler that compiles a restricted Starlark script into WASM bytecode.

---
```shell
 sbt "run src/resources/examples/<something>.star output.wasm"
```
and run this output in node.js using `run.js`

```txt
[info] running com.example.Main src/resources/examples/arithmetic.star output.wasm
Starting with args: src/resources/examples/arithmetic.star, output.wasm
Reading from src/resources/examples/arithmetic.star, writing to output.wasm
Source code:
x = 5
y = 10
x + y * 2

Successfully parsed program
Generated 53 bytes of WASM
Successfully wrote to output.wasm
[success] Total time: 1 s, completed 28-Apr-2025, 5:01:11 pm
```
### Output :
for example [src/resources/examples/arithmetic.star](https://github.com/qxrein/crimson-star/blob/main/src/resources/examples/arithmetic.star)
```bash
> : wasm-objdump -x output.wasm

output.wasm:	file format wasm 0x1

Section Details:

Type[1]:
 - type[0] () -> i32
Function[1]:
 - func[0] sig=0 <main>
Export[1]:
 - func[0] <main> -> "main"
Code[1]:
 - func[0] size=20 <main>

> : hexdump -C output.wasm | head

00000000  00 61 73 6d 01 00 00 00  01 05 01 60 00 01 7f 03  |.asm.......`....|
00000010  02 01 00 07 08 01 04 6d  61 69 6e 00 00 0a 16 01  |.......main.....|
00000020  14 01 02 7f 41 05 21 00  41 0a 21 01 20 00 20 01  |....A.!.A.!. . .|
00000030  6a 41 02 6c 0b                                    |jA.l.|
00000035
```
## Contributing
---
All contributors must follow the [Code of Conduct](https://github.com/qxrein/crimson-star/blob/main/CODE_OF_CONDUCT.md).
## License
---
All code under this repository is licensed under the [MIT](https://github.com/qxrein/crimson-star/blob/main/LICENSE) license.
