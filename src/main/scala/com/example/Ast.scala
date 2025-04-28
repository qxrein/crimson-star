package com.example

sealed trait Expr
case class IntLit(value: Int) extends Expr
case class Ident(name: String) extends Expr
case class BinOp(left: Expr, op: String, right: Expr) extends Expr
case class Let(name: String, value: Expr) extends Expr
case class FnDef(name: String, params: List[String], body: Expr) extends Expr
case class FnCall(name: String, args: List[Expr]) extends Expr

case class Program(exprs: List[Expr])
