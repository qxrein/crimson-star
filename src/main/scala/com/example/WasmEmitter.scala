package com.example

import java.nio.{ByteBuffer, ByteOrder}
import scala.collection.mutable

class WasmEmitter {
  private val buffer = ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN)
  private var localCount = 0
  private val locals = mutable.Map[String, Int]()

  def emitProgram(program: Program): Array[Byte] = {
    buffer.clear()
    putMagicAndVersion()
    emitTypeSection()
    emitFunctionSection()
    emitExportSection()
    emitCodeSection(program)
    buffer.array().take(buffer.position())
  }

  private def putMagicAndVersion(): Unit = {
    buffer.put(0x00.toByte)
    buffer.put(0x61.toByte)
    buffer.put(0x73.toByte)
    buffer.put(0x6d.toByte)
    buffer.put(0x01.toByte)
    buffer.put(0x00.toByte)
    buffer.put(0x00.toByte)
    buffer.put(0x00.toByte)
  }

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

  private def emitTypeSection(): Unit = {
    val sectionBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
    sectionBuffer.put(0x01.toByte)
    sectionBuffer.put(0x60.toByte)
    sectionBuffer.put(0x00.toByte)
    sectionBuffer.put(0x01.toByte)
    sectionBuffer.put(0x7F.toByte)
    buffer.put(0x01.toByte)
    putLEB128(sectionBuffer.position())
    buffer.put(sectionBuffer.array().take(sectionBuffer.position()))
  }

  private def emitFunctionSection(): Unit = {
    val sectionBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
    sectionBuffer.put(0x01.toByte)
    sectionBuffer.put(0x00.toByte)
    buffer.put(0x03.toByte)
    putLEB128(sectionBuffer.position())
    buffer.put(sectionBuffer.array().take(sectionBuffer.position()))
  }

  private def emitExportSection(): Unit = {
    val sectionBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
    sectionBuffer.put(0x01.toByte)
    sectionBuffer.put(0x04.toByte)
    "main".getBytes.foreach(sectionBuffer.put)
    sectionBuffer.put(0x00.toByte)
    sectionBuffer.put(0x00.toByte)
    buffer.put(0x07.toByte)
    putLEB128(sectionBuffer.position())
    buffer.put(sectionBuffer.array().take(sectionBuffer.position()))
  }

  private def emitCodeSection(program: Program): Unit = {
    val sectionBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
    sectionBuffer.put(0x01.toByte)
    val bodyBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
    localCount = 0
    locals.clear()
    println("Starting code emission")
    program.exprs.zipWithIndex.foreach { case (expr, idx) =>
      val isLast = idx == program.exprs.length - 1
      println(s"Emitting expr: $expr, isLast: $isLast")
      emitExpr(expr, bodyBuffer, isLast)
    }
    val localDeclBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
    if (localCount > 0) {
      localDeclBuffer.put(0x01.toByte)
      putLEB128(localDeclBuffer, localCount)
      localDeclBuffer.put(0x7F.toByte)
    } else {
      localDeclBuffer.put(0x00.toByte)
    }
    bodyBuffer.flip()
    val finalBodyBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
    finalBodyBuffer.put(localDeclBuffer.array().take(localDeclBuffer.position()))
    finalBodyBuffer.put(bodyBuffer)
    if (program.exprs.isEmpty) {
      finalBodyBuffer.put(0x41.toByte)
      putLEB128(finalBodyBuffer, 0)
    }
    finalBodyBuffer.put(0x0b.toByte)
    putLEB128(sectionBuffer, finalBodyBuffer.position())
    sectionBuffer.put(finalBodyBuffer.array().take(finalBodyBuffer.position()))
    buffer.put(0x0a.toByte)
    putLEB128(sectionBuffer.position())
    buffer.put(sectionBuffer.array().take(sectionBuffer.position()))
  }

  private def emitExpr(expr: Expr, buf: ByteBuffer, isLast: Boolean): Unit = expr match {
    case IntLit(value) =>
      println(s"Emitting IntLit($value)")
      buf.put(0x41.toByte)
      putLEB128(buf, value)
    case BinOp(left, op, right) =>
      println(s"Emitting BinOp($left, $op, $right)")
      emitExpr(left, buf, false)
      emitExpr(right, buf, false)
      op match {
        case "+" => buf.put(0x6a.toByte)
        case "-" => buf.put(0x6b.toByte)
        case "*" => buf.put(0x6c.toByte)
        case "/" => buf.put(0x6d.toByte)
      }
    case Let(name, value) =>
      println(s"Emitting Let($name, $value)")
      emitExpr(value, buf, false)
      if (!locals.contains(name)) {
        locals(name) = localCount
        println(s"Assigned local: $name = $localCount")
        localCount += 1
      }
      buf.put(0x21.toByte)
      putLEB128(buf, locals(name))
      if (isLast) {
        println(s"Last Let, returning local: $name")
        buf.put(0x20.toByte)
        putLEB128(buf, locals(name))
      }
    case Ident(name) =>
      if (!locals.contains(name)) {
        throw new IllegalStateException(s"Undefined variable: $name")
      }
      println(s"Emitting Ident($name), local: ${locals(name)}")
      buf.put(0x20.toByte)
      putLEB128(buf, locals(name))
    case FnDef(name, params, body) =>
      println(s"Emitting FnDef($name, $params, $body)")
      emitExpr(body, buf, isLast)
      if (isLast && !leavesValue(body)) {
        buf.put(0x41.toByte)
        putLEB128(buf, 0)
      }
    case FnCall(name, args) =>
      println(s"Emitting FnCall($name, $args)")
      args.foreach(emitExpr(_, buf, false))
      args.foreach(_ => buf.put(0x1a.toByte))
      if (isLast) {
        buf.put(0x41.toByte)
        putLEB128(buf, 0)
      }
  }

  private def leavesValue(expr: Expr): Boolean = expr match {
    case IntLit(_) => true
    case BinOp(_, _, _) => true
    case Ident(_) => true
    case Let(_, _) => false
    case FnDef(_, _, body) => leavesValue(body)
    case FnCall(_, _) => false
  }
}
