package sample.stream

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}

import scala.concurrent.Future
import scala.trace._
import scala.util.Try

object BasicTransformation {

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem("Sys")
    import system.dispatcher

    implicit val materializer: ActorMaterializer = ActorMaterializer()

    val text: String =
      """|Lorem Ipsum is simply dummy text of the printing and typesetting industry.
         |Lorem Ipsum has been the industry's standard dummy text ever since the 1500s,
         |when an unknown printer took a galley of type and scrambled it to make a type
         |specimen book.""".stripMargin

    val source: Source[String, Unit] // made up of strings. Returns nothing
      = Source.fromIterator(() => text.split("\\s").iterator)

    val foldedSource: Future[String]
      = source.runFold("")((acc: String, e: String) => acc + e )(materializer)

    foldedSource.onComplete( (s: Try[String]) => s match {
        case scala.util.Success(s) => println(s + Pos());
        case scala.util.Failure(f) => println(f + Pos());
      }
    )(dispatcher)

    val uppercase: source.Repr[String] = source.map((i: String) =>  i.toUpperCase)

    val publisher: Future[Unit] = uppercase.runWith(Sink.foreach(println))(materializer)
      // .runForeach(println).
    Thread.sleep(100)
    publisher.onComplete( (s: Try[Unit]) => s match {
        case scala.util.Success(s) => println(s + Pos()); system.shutdown()
        case scala.util.Failure(f) => println(f + Pos()); system.shutdown()
        }
      )(dispatcher)
    println("Done" + Pos())
    // could also use .runWith(Sink.foreach(println)) instead of .runForeach(println) above
    // as it is a shorthand for the same thing. Sinks may be constructed elsewhere and plugged
    // in like this. Note also that foreach returns a future (in either form) which may be
    // used to attach lifecycle events to, like here with the onComplete.
  }
}
