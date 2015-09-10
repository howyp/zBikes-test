name := """zBikes-test"""

version := "1.0"

scalaVersion := "2.11.7"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.4"

libraryDependencies += "org.scalaj" %% "scalaj-http" % "1.1.5"

libraryDependencies += "com.github.tomakehurst" % "wiremock" % "1.57"

libraryDependencies += "io.spray" %%  "spray-json" % "1.3.2"

mainClass in compile := Some("TestRunner")