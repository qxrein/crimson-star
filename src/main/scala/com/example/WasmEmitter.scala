package com.example

import java.nio.{ByteBuffer, ByteOrder}
import scala.collection.mutable

class WasmEmitter {
  private val buffer = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
  private var localCount = 0
  private val locals = mutable.Map[String, Int]()
  
  def emitProgram(program: Program): Array[Byte] = {
    buffer.clear()
    
    // Module header
    putMagicAndVersion()
    
    // Type section
    emitTypeSection()
    
    // Function section
    emitFunctionSection()
    
    // Export section
    emitExportSection()
    
    // Code section
    emitCodeSection(program)
    
    buffer.array().take(buffer.position())
  }
  
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
  
  private def emitTypeSection(): Unit = {
    // Type section header
    buffer.put(0x01.toByte) // section ID
    buffer.put(0x08.toByte) // section size
    buffer.put(0x01.toByte) // number of types
    
    // Function type (params and results)
    buffer.put(0x60.toByte) // func type
    buffer.put(0x00.toByte) // 0 params
    buffer.put(0x01.toByte) // 1 result
    buffer.put(0x7F.toByte) // i32
  }
  
  private def emitFunctionSection(): Unit = {
    buffer.put(0x03.toByte) // section ID
    buffer.put(0x02.toByte) // section size
    buffer.put(0x01.toByte) // number of functions
    buffer.put(0x00.toByte) // type index 0
  }
  
  private def emitExportSection(): Unit = {
    buffer.put(0x07.toByte) // section ID
    buffer.put(0x06.toByte) // section size
    buffer.put(0x01.toByte) // number of exports
    
    // Export name "main"
    buffer.put(0x04.toByte) // name length
    "main".getBytes.foreach(buffer.put)
    
    buffer.put(0x00.toByte) // export kind (function)
    buffer.put(0x00.toByte) // function index 0
  }
  
  private def emitCodeSection(program: Program): Unit = {
    val codeBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
    
    // Function body
    codeBuffer.put(0x0a.toByte) // code section ID
    val bodySizePos = codeBuffer.position()
    codeBuffer.putInt(0) // placeholder for body size
    
    // Local declarations
    codeBuffer.put(0x01.toByte) // number of local blocks
    codeBuffer.put(0x01.toByte) // number of locals in block
    codeBuffer.put(0x7F.toByte) // i32
    
    // Emit all expressions
    program.exprs.foreach(emitExpr(_, codeBuffer))
    
    // End function
    codeBuffer.put(0x0b.toByte) // end
    
    // Go back and write body size
    val bodySize = codeBuffer.position() - bodySizePos - 4
    codeBuffer.putInt(bodySizePos, bodySize)
    
    // Write code section to main buffer
    buffer.put(codeBuffer.array().take(codeBuffer.position()))
  }
  
  private def emitExpr(expr: Expr, buf: ByteBuffer): Unit = expr match {
    case IntLit(value) =>
      buf.put(0x41.toByte) // i32.const
      buf.putInt(value)
      
    case BinOp(left, op, right) =>
      emitExpr(left, buf)
      emitExpr(right, buf)
      op match {
        case "+" => buf.put(0x6a.toByte) // i32.add
        case "-" => buf.put(0x6b.toByte) // i32.sub
        case "*" => buf.put(0x6c.toByte) // i32.mul
        case "/" => buf.put(0x6d.toByte) // i32.div_s
      }
      
    case Let(name, value) =>
      emitExpr(value, buf)
      locals(name) = localCount
      buf.put(0x21.toByte) // local.set
      buf.put(localCount.toByte)
      localCount += 1
      
    case Ident(name) =>
      buf.put(0x20.toByte) // local.get
      buf.put(locals(name).toByte)
      
    case FnDef(name, params, body) =>
      // TODO: Implement function definition
      emitExpr(body, buf)
      
    case FnCall(name, args) =>
      args.foreach(emitExpr(_, buf))
      // TODO: Implement function call
  }
}
