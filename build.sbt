name := "AwesomePolls"

version := "0.1"

scalaVersion := "2.13.1"

libraryDependencies ++= Seq(
  "org.augustjune" %% "canoe" % "0.4.1",
  "org.slf4j" % "slf4j-api" % "1.7.5",
  "org.slf4j" % "slf4j-simple" % "1.7.5",
  "com.github.pureconfig" %% "pureconfig" % "0.12.3",
  "org.scalatest" %% "scalatest" % "3.1.0" % Test,
  "org.scalamock" %% "scalamock" % "4.4.0" % Test
)