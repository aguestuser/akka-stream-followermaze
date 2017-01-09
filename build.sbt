name := "followermaze"

version := "1.0"

scalaVersion := "2.12.1"

libraryDependencies ++= {

  val akkaVersion = "2.4.16"

  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion,
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion
  )
}
