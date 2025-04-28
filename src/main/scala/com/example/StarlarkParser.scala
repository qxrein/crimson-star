package com.example

import scala.util.parsing.combinator._

class StarlarkParser extends RegexParsers {
  def program: Parser[Program] = rep(expr) ^^ (exps => Program(exps))
  
  def expr: Parser[Expr] = 
    intLit | let | fnDef | fnCall | binOp | ident
  
  def intLit: Parser[IntLit] = """-?\d+""".r ^^ (s => IntLit(s.toInt))
  
  def ident: Parser[Ident] = """[a-zA-Z_][a-zA-Z0-9_]*""".r ^^ (name => Ident(name))

  def let: Parser[Let] = (ident <~ "=") ~ expr ^^ { case id ~ e => Let(id.name, e) }
  
  def fnDef: Parser[FnDef] = 
    ("def" ~> ident) ~ ("(" ~> repsep(ident, ",") <~ ")") ~ ("=" ~> expr) ^^ {
      case id ~ params ~ body => FnDef(id.name, params.map(_.name), body)
    }
    
  def fnCall: Parser[FnCall] = 
    ident ~ ("(" ~> repsep(expr, ",") <~ ")") ^^ { case id ~ args => FnCall(id.name, args) }
    
  def binOp: Parser[BinOp] = 
    expr ~ ("+" | "-" | "*" | "/") ~ expr ^^ { case l ~ op ~ r => BinOp(l, op, r) }
  
    
  def parse(input: String): Either[String, Program] = 
    parseAll(program, input) match {
      case Success(result, _) => Right(result)
      case NoSuccess(msg, next) => Left(s"Error at ${next.pos}: $msg")
      case Failure(msg, next) => Left(s"Failure at ${next.pos}: $msg")
      case Error(msg, next) => Left(s"Error at ${next.pos}: $msg")
    }
}
