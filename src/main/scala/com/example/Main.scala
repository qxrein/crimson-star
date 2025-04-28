package com.example

import java.nio.file.{Files, Paths}
import scala.io.Source

object Main {
  def main(args: Array[String]): Unit = {
    if (args.length < 1) {
      println("Usage: sbt run <input.star> [output.wasm]")
      System.exit(1)
    }
    
    val inputFile = args(0)
    val outputFile = if (args.length > 1) args(1) else "output.wasm"
    
    val source = Source.fromFile(inputFile).mkString
    val parser = new StarlarkParser()
    
    parser.parse(source) match {
      case Right(program) =>
        val emitter = new WasmEmitter()
        val wasmBytes = emitter.emitProgram(program)
        Files.write(Paths.get(outputFile), wasmBytes)
        println(s"Successfully compiled $inputFile to $outputFile")
        
      case Left(error) =>
        println(s"Compilation error: $error")
        System.exit(1)
    }
  }
}
