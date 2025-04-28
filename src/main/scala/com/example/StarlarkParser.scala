package com.example

import scala.util.parsing.combinator._

class StarlarkParser extends RegexParsers {
  override def skipWhitespace = true

  def program: Parser[Program] = rep1(expr) ^^ Program
  
  def expr: Parser[Expr] = simpleExpr ~ rep(operator ~ simpleExpr) ^^ {
    case first ~ rest => rest.foldLeft(first) {
      case (left, op ~ right) => BinOp(left, op, right)
    }
  }
  
  def simpleExpr: Parser[Expr] = (
    intLit 
    | let 
    | fnDef 
    | fnCall 
    | ident
    | "(" ~> expr <~ ")"
  )
  
  def operator: Parser[String] = "+" | "-" | "*" | "/"
  
  def intLit: Parser[IntLit] = """-?\d+""".r ^^ (s => IntLit(s.toInt))
  
  def ident: Parser[Ident] = """[a-z_]\w*""".r ^^ Ident
  
  def let: Parser[Let] = (ident <~ "=") ~ expr ^^ { 
    case id ~ e => Let(id.name, e) 
  }
  
  def fnDef: Parser[FnDef] = 
    ("def" ~> ident) ~ ("(" ~> repsep(ident, ",") <~ ")") ~ ("=" ~> expr) ^^ {
      case id ~ params ~ body => FnDef(id.name, params.map(_.name), body)
    }
    
  def fnCall: Parser[FnCall] = 
    ident ~ ("(" ~> repsep(expr, ",") <~ ")") ^^ { case id ~ args => FnCall(id.name, args) }
  
  def parse(input: String): Either[String, Program] = 
    parseAll(program, input) match {
      case Success(result, _) => Right(result)
      case NoSuccess(msg, next) => Left(s"Error at ${next.pos}: $msg")
    }
}
