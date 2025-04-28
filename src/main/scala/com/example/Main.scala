package com.example

import java.nio.file.{Files, Paths}
import scala.io.Source

object Main {
  def main(args: Array[String]): Unit = {
    println(s"Starting with args: ${args.mkString(", ")}")
    
    if (args.length < 1) {
      println("Usage: sbt run <input.star> [output.wasm]")
      System.exit(1)
    }
    
    val inputFile = args(0)
    val outputFile = if (args.length > 1) args(1) else "output.wasm"
    
    println(s"Reading from $inputFile, writing to $outputFile")
    
    try {
      val source = Source.fromFile(inputFile).mkString
      println(s"Source code:\n$source")
      
      val parser = new StarlarkParser()
      val parseResult = parser.parse(source)
      
      parseResult match {
        case Right(program) =>
          println("Successfully parsed program")
          val emitter = new WasmEmitter()
          val wasmBytes = emitter.emitProgram(program)
          println(s"Generated ${wasmBytes.length} bytes of WASM")
          
          Files.write(Paths.get(outputFile), wasmBytes)
          println(s"Successfully wrote to $outputFile")
          
        case Left(error) =>
          println(s"Parse error: $error")
          System.exit(1)
      }
    } catch {
      case e: Exception =>
        println(s"Error: ${e.getMessage}")
        e.printStackTrace()
        System.exit(1)
    }
  }
}
