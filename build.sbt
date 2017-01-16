name := "followermaze"

version := "1.0"

scalaVersion := "2.12.1"

libraryDependencies ++= {

  val akkaVersion = "2.4.16"
  val scalaTestVersion = "3.0.0"
  val scalazVersion = "7.2.8"
  //val scalaMockVersion = "3.4.2"

  Seq(
    "com.typesafe.akka" %% "akka-actor"                  % akkaVersion,
    "com.typesafe.akka" %% "akka-stream"                 % akkaVersion,
    "org.scalaz"        %% "scalaz-core"                 % scalazVersion,

    "org.scalatest"     %% "scalatest"                   % scalaTestVersion % Test,
    //"org.scalamock"     %% "scalamock-scalatest-support" % scalaMockVersion % Test,
    "com.typesafe.akka" %% "akka-stream-testkit"         % akkaVersion      % Test,
    "com.typesafe.akka" %% "akka-testkit"                % akkaVersion      % Test
  )
}
