import scalariform.formatter.preferences._

name := "akka-stream-scala"

version := "1.1"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "com.twitter" %% "util-collection" % "6.34.0",
  "com.typesafe.akka" %% "akka-stream-experimental" % "2.0.1"
)

resolvers += "johnreed2 bintray" at "http://dl.bintray.com/content/johnreed2/maven"

libraryDependencies += "scala.trace" %% "scala-trace-debug" % "2.2.17"

scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(PreserveDanglingCloseParenthesis, true)

fork in run := true
