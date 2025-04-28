package com.example

import scala.util.parsing.combinator._

// Import AST types from Ast.scala
import com.example.{Expr, Program, IntLit, BinOp, Let, Ident, FnDef, FnCall}

object StarlarkParser extends RegexParsers {
  override def skipWhitespace = true
  override val whiteSpace = """[ \t\r\f\n]+""".r // Include \n in whitespace

  def program: Parser[Program] = rep(statement) ^^ { stmts => Program(stmts) }

  def statement: Parser[Expr] =
    (let | fnDef | expr) <~ opt("\n") // Allow optional newline after statements

  def let: Parser[Let] =
    ident ~ "=" ~ expr ^^ { case id ~ _ ~ e => Let(id, e) }

  def ident: Parser[String] =
    """[a-zA-Z_][a-zA-Z0-9_]*""".r

  def fnDef: Parser[FnDef] =
    "def" ~ ident ~ "(" ~ repsep(ident, ",") ~ ")" ~ "=" ~ expr ^^ {
      case _ ~ name ~ _ ~ params ~ _ ~ _ ~ body => FnDef(name, params, body)
    }

  // Expression parser with operator precedence
  def expr: Parser[Expr] = addSub

  def addSub: Parser[Expr] =
    mulDiv ~ rep(("+" | "-") ~ mulDiv) ^^ {
      case left ~ ops => ops.foldLeft(left) {
        case (acc, op ~ right) => BinOp(acc, op, right)
      }
    }

  def mulDiv: Parser[Expr] =
    factor ~ rep(("*" | "/") ~ factor) ^^ {
      case left ~ ops => ops.foldLeft(left) {
        case (acc, op ~ right) => BinOp(acc, op, right)
      }
    }

  def factor: Parser[Expr] =
    "(" ~> expr <~ ")" | intLit | ident ^^ Ident.apply | fnCall

  def intLit: Parser[IntLit] =
    """-?\d+""".r ^^ { n => IntLit(n.toInt) }

  def fnCall: Parser[FnCall] =
    ident ~ "(" ~ repsep(expr, ",") ~ ")" ^^ {
      case name ~ _ ~ args ~ _ => FnCall(name, args)
    }

  def parse(input: String): Program = parseAll(program, input) match {
    case Success(result, _) => result
    case failure: NoSuccess => throw new IllegalArgumentException(s"Parse error: ${failure.msg}")
  }
}
