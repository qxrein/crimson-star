package com.example

import java.nio.{ByteBuffer, ByteOrder}
import scala.collection.mutable

class WasmEmitter {
  private val buffer = ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN)
  private var localCount = 0
  private val locals = mutable.Map[String, Int]()

  // Emits a complete WASM module for the given program
  def emitProgram(program: Program): Array[Byte] = {
    buffer.clear()

    // WASM module header
    putMagicAndVersion()

    // Type section: defines function types
    emitTypeSection()

    // Function section: declares functions
    emitFunctionSection()

    // Export section: exports the main function
    emitExportSection()

    // Code section: contains function bodies
    emitCodeSection(program)

    buffer.array().take(buffer.position())
  }

  // Writes the WASM magic number and version
  private def putMagicAndVersion(): Unit = {
    buffer.put(0x00.toByte)
    buffer.put(0x61.toByte)
    buffer.put(0x73.toByte)
    buffer.put(0x6d.toByte) // "\0asm"
    buffer.put(0x01.toByte)
    buffer.put(0x00.toByte)
    buffer.put(0x00.toByte)
    buffer.put(0x00.toByte) // version 1
  }

  // Encodes an integer in LEB128 format to the main buffer
  private def putLEB128(value: Int): Unit = {
    var v = value
    while (true) {
      var byte = (v & 0x7F)
      v >>= 7
      if (v == 0) {
        buffer.put(byte.toByte)
        return
      }
      byte |= 0x80
      buffer.put(byte.toByte)
    }
  }

  // Encodes an integer in LEB128 format to the specified buffer
  private def putLEB128(buf: ByteBuffer, value: Int): Unit = {
    var v = value
    while (true) {
      var byte = (v & 0x7F)
      v >>= 7
      if (v == 0) {
        buf.put(byte.toByte)
        return
      }
      byte |= 0x80
      buf.put(byte.toByte)
    }
  }

  // Emits the type section: one function type () -> i32
  private def emitTypeSection(): Unit = {
    val sectionBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
    
    sectionBuffer.put(0x01.toByte) // 1 type
    sectionBuffer.put(0x60.toByte) // func type
    sectionBuffer.put(0x00.toByte) // 0 params
    sectionBuffer.put(0x01.toByte) // 1 result
    sectionBuffer.put(0x7F.toByte) // i32

    buffer.put(0x01.toByte) // section ID
    putLEB128(sectionBuffer.position()) // section size
    buffer.put(sectionBuffer.array().take(sectionBuffer.position()))
  }

  // Emits the function section: one function with type 0
  private def emitFunctionSection(): Unit = {
    val sectionBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
    
    sectionBuffer.put(0x01.toByte) // 1 function
    sectionBuffer.put(0x00.toByte) // type index 0

    buffer.put(0x03.toByte) // section ID
    putLEB128(sectionBuffer.position()) // section size
    buffer.put(sectionBuffer.array().take(sectionBuffer.position()))
  }

  // Emits the export section: exports "main" function
  private def emitExportSection(): Unit = {
    val sectionBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
    
    sectionBuffer.put(0x01.toByte) // 1 export
    sectionBuffer.put(0x04.toByte) // name length
    "main".getBytes.foreach(sectionBuffer.put)
    sectionBuffer.put(0x00.toByte) // export kind (function)
    sectionBuffer.put(0x00.toByte) // function index 0

    buffer.put(0x07.toByte) // section ID
    putLEB128(sectionBuffer.position()) // section size
    buffer.put(sectionBuffer.array().take(sectionBuffer.position()))
  }

  // Emits the code section: one function body with locals and instructions
  private def emitCodeSection(program: Program): Unit = {
    val sectionBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
    
    sectionBuffer.put(0x01.toByte) // 1 function
    
    val bodyBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
    
    // Reset local count for this function
    localCount = 0
    locals.clear()

    // Emit all expressions, ensuring the last one leaves a value
    program.exprs.zipWithIndex.foreach { case (expr, idx) =>
      val isLast = idx == program.exprs.length - 1
      emitExpr(expr, bodyBuffer, isLast)
    }

    // Local declarations (after emitting expressions to know localCount)
    val localDeclBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
    if (localCount > 0) {
      localDeclBuffer.put(0x01.toByte) // 1 local block
      putLEB128(localDeclBuffer, localCount) // number of locals
      localDeclBuffer.put(0x7F.toByte) // i32
    } else {
      localDeclBuffer.put(0x00.toByte) // 0 local blocks
    }

    // Combine local declarations and body
    bodyBuffer.flip()
    val finalBodyBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
    finalBodyBuffer.put(localDeclBuffer.array().take(localDeclBuffer.position()))
    finalBodyBuffer.put(bodyBuffer)

    // If no expressions, return 0
    if (program.exprs.isEmpty) {
      finalBodyBuffer.put(0x41.toByte) // i32.const
      putLEB128(finalBodyBuffer, 0) // 0
    }

    // End function
    finalBodyBuffer.put(0x0b.toByte) // end

    // Write function body size and content
    putLEB128(sectionBuffer, finalBodyBuffer.position()) // body size
    sectionBuffer.put(finalBodyBuffer.array().take(finalBodyBuffer.position()))
    
    // Write code section
    buffer.put(0x0a.toByte) // section ID
    putLEB128(sectionBuffer.position()) // section size
    buffer.put(sectionBuffer.array().take(sectionBuffer.position()))
  }

  // Emits WASM instructions for an expression, ensuring stack correctness
  private def emitExpr(expr: Expr, buf: ByteBuffer, isLast: Boolean): Unit = expr match {
    case IntLit(value) =>
      buf.put(0x41.toByte) // i32.const
      putLEB128(buf, value)
      
    case BinOp(left, op, right) =>
      emitExpr(left, buf, false)
      emitExpr(right, buf, false)
      op match {
        case "+" => buf.put(0x6a.toByte) // i32.add
        case "-" => buf.put(0x6b.toByte) // i32.sub
        case "*" => buf.put(0x6c.toByte) // i32.mul
        case "/" => buf.put(0x6d.toByte) // i32.div_s
      }
      
    case Let(name, value) =>
      emitExpr(value, buf, false)
      if (!locals.contains(name)) {
        locals(name) = localCount
        localCount += 1
      }
      buf.put(0x21.toByte) // local.set
      putLEB128(buf, locals(name))
      // Return the value if last expression
      if (isLast) {
        buf.put(0x20.toByte) // local.get
        putLEB128(buf, locals(name))
      }
      
    case Ident(name) =>
      if (!locals.contains(name)) {
        throw new IllegalStateException(s"Undefined variable: $name")
      }
      buf.put(0x20.toByte) // local.get
      putLEB128(buf, locals(name))
      
    case FnDef(name, params, body) =>
      // Placeholder: emit body, treat as let binding for simplicity
      emitExpr(body, buf, isLast)
      if (isLast && !leavesValue(body)) {
        buf.put(0x41.toByte) // i32.const
        putLEB128(buf, 0) // return 0
      }
      
    case FnCall(name, args) =>
      // Placeholder: emit args, return 0
      args.foreach(emitExpr(_, buf, false))
      args.foreach(_ => buf.put(0x1a.toByte)) // drop each arg
      if (isLast) {
        buf.put(0x41.toByte) // i32.const
        putLEB128(buf, 0) // return 0
      }
  }

  // Helper to determine if an expression leaves a value on the stack
  private def leavesValue(expr: Expr): Boolean = expr match {
    case IntLit(_) => true
    case BinOp(_, _, _) => true
    case Ident(_) => true
    case Let(_, _) => false
    case FnDef(_, _, body) => leavesValue(body)
    case FnCall(_, _) => false
  }
}
