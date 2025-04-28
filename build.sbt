name := "starlark-wasm-compiler"
version := "0.1.0"
scalaVersion := "3.3.1"

libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-parser-combinators" % "2.3.0",
  "org.scalatest" %% "scalatest" % "3.2.17" % Test
)
