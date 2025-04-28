package com.example

import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets

object Main {
  def main(args: Array[String]): Unit = {
    if (args.length != 2) {
      println("Usage: <input.star> <output.wasm>")
      sys.exit(1)
    }
    println(s"Starting with args: ${args.mkString(", ")}")
    val inputPath = args(0)
    val outputPath = args(1)
    println(s"Reading from $inputPath, writing to $outputPath")
    val source = new String(Files.readAllBytes(Paths.get(inputPath)), StandardCharsets.UTF_8)
    println("Source code:")
    println(source)
    val parser = StarlarkParser
    val program = parser.parse(source)
    println("AST:")
    println(program)
    println("Successfully parsed program")
    val emitter = new WasmEmitter()
    val wasmBytes = emitter.emitProgram(program)
    println(s"Generated ${wasmBytes.length} bytes of WASM")
    Files.write(Paths.get(outputPath), wasmBytes)
    println(s"Successfully wrote to $outputPath")
  }
}
