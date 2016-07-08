package sample.stream

import java.io.File

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, Graph, UniformFanOutShape}
import akka.stream.scaladsl._
import akka.util.ByteString

import scala.concurrent.Future
import scala.concurrent.forkjoin.ThreadLocalRandom
import scala.util.{Failure, Success}

object WritePrimes {

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem("Sys")
    import system.dispatcher
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    // generate random numbers
    val maxRandomNumberSize = 1000000
    val primeSource: Source[Int, Unit] =
      Source.fromIterator(() => Iterator.continually(ThreadLocalRandom.current().nextInt(maxRandomNumberSize))).
        // filter prime numbers
        filter(rnd => isPrime(rnd)).
        // and neighbor +2 is also prime
        filter(prime => isPrime(prime + 2))

    // write to file sink
    val fileSink: Sink[ByteString, Future[Long]] = FileIO.toFile(new File("target/primes.txt"))
    val slowSink: Sink[Int, Future[Long]] = Flow[Int]
      // act as if processing is really slow
      .map(i => { Thread.sleep(1000); ByteString(i.toString) })
      .toMat(fileSink)((_, bytesWritten) => bytesWritten)

    // console output sink
    val consoleSink: Sink[Int, Future[Unit]] = Sink.foreach[Int](println)

    // send primes to both slow file sink and console sink using graph API
    val graph: Graph[ClosedShape.type, Future[Long]] = GraphDSL.create(slowSink, consoleSink)((slow, _) => slow) { implicit builder =>
      (slow, console) =>
        import GraphDSL.Implicits._
        val broadcast: UniformFanOutShape[Int, Int] = builder.add(Broadcast[Int](2)) // the splitter - like a Unix tee
        primeSource ~> broadcast ~> slow // connect primes to splitter, and one side to file
        broadcast ~> console // connect other side of splitter to console
        ClosedShape
    }
    val materialized: Future[Long] = RunnableGraph.fromGraph(graph).run()

    // ensure the output file is closed and the system shutdown upon completion
    materialized.onComplete {
      case Success(_) =>
        system.shutdown()
      case Failure(e) =>
        println(s"Failure: ${e.getMessage}")
        system.shutdown()
    }

  }

  def isPrime(n: Int): Boolean = {
    if (n <= 1) false
    else if (n == 2) true
    else !(2 to (n - 1)).exists(x => n % x == 0)
  }
}
