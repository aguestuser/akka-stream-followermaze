package com.example

import akka.Done
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.scaladsl.Tcp
import akka.stream.{ActorMaterializer, Materializer}
import com.example.dispatch.DispatchActor
import com.example.dispatch.DispatchFlows.{eventSourceFlow, subscribeFlow}

import scala.concurrent.{ExecutionContextExecutor, Future}

trait MainTrait {

  implicit val system: ActorSystem = ActorSystem()
  implicit def executor: ExecutionContextExecutor = system.dispatcher
  implicit val materializer: Materializer = ActorMaterializer()
  implicit val dispatchActor: ActorRef = system.actorOf(Props[DispatchActor])

  val host = "127.0.0.1"
  val eventSourcePort = 9090
  val userClientPort = 9099

  def run(): Future[Seq[Done]] = {
    println(s"+ Server online at $host")
    Future.sequence(Seq(
      createUserClientSocket(),
      createEventSourceSocket()))
  }

  def createUserClientSocket(): Future[Done] = {
    val userClientSocket =Tcp().bind(host, userClientPort)
    userClientSocket runForeach { connection =>
      println(s"+ User client connected on: ${connection.remoteAddress}")
      connection.handleWith(subscribeFlow)
    }
  }

  def createEventSourceSocket(): Future[Done] = {
    val eventSourceSocket = Tcp().bind(host, eventSourcePort)
    eventSourceSocket runForeach { connection =>
      println(s"+ Event Source connected on: ${connection.remoteAddress}")
      connection.handleWith(eventSourceFlow)
    }
  }
}


object Main extends App with MainTrait {
  run()
}
