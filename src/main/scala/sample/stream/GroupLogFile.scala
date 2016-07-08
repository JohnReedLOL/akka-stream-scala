package sample.stream

import java.io.File

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.io.Framing
import akka.stream.scaladsl._
import akka.stream.stage.{Context, StatefulStage, SyncDirective}
import akka.util.ByteString

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.util.matching.Regex
import scala.trace._

object GroupLogFile {

  def main(args: Array[String]): Unit = {
    // actor system and implicit materializer
    implicit val system: ActorSystem = ActorSystem("Sys")
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    // execution context sample/stream/GroupLogFile.scala

    val LoglevelPattern: Regex = """.*\[(DEBUG|INFO|WARN|ERROR)\].*""".r

    // read lines from a log file
    val logFile: File = new File("src/main/resources/logfile.txt")

    val source: Source[ByteString, Future[Long]]  // made up of byteStrings
      = FileIO.fromFile(logFile) // materializes a Future[Long]

      val framer: Flow[ByteString, ByteString, Unit] = Framing.delimiter(
        delimiter = ByteString(System.lineSeparator),
        maximumFrameLength = 512,
        allowTruncation = true
      )

      // parse chunks of bytes into lines
      val chunkedSource: source.Repr[ByteString]
        = source.via[ByteString, Unit](flow = framer) // break up source using framer

      val utf8ChunkSource: Source[String, Future[Long]] // made up of strings.
        = chunkedSource.map(_.utf8String)

      val logLevelSource: Source[(String, String), Future[Long]] =
          utf8ChunkSource.map { // Extract log level
        case line@LoglevelPattern(logLevel) => (logLevel, line)
        case line@other => ("OTHER", line)
      }

      // group them by log level. Represents map of Log-level to things at that log level
      // 5 substreams - Debug, Error, Info, Other, Warn
      // types = SubFlow[Out, Mat, Repr, Closed]. Output = (String, String), Materializes = Future[Long]
      val streamOfStreams
        : SubFlow[(String, String), Future[Long], logLevelSource.Repr, logLevelSource.Closed]
        = logLevelSource.groupBy(maxSubstreams = 5, _._1)

      //val mergedSubstreams: Source[_, Future[Long]][(String, String)]
      //  = substreams.mergeSubstreamsWithParallelism(Int.MaxValue);

      val foldedStreams
        = streamOfStreams.fold(("", List.empty[String])) {
        case ((_, list), (logLevel, line)) => (logLevel, line :: list)
      }
      // list contains all lines in order
      // logLevel is just the logLevel of the last element in the list

      // Produces a List(String, List[String])

      // write lines of each group to a separate file
      val mappedWriter
        = foldedStreams.mapAsync(parallelism = 5) {
        case (level, groupList) => {
          val reverseSource: Source[String, Unit] = Source(groupList.reverse)

          val delimitedSource: reverseSource.Repr[ByteString]
            = reverseSource.map(line => ByteString(line + "\n"))

          println("Level: " + level + " List: " + groupList + Pos() + "\n")
          val result: Future[Long]
            = delimitedSource.runWith(FileIO.toFile(new File(s"target/log-$level.txt")))
          result
        }
      }
      val mergedStream: logLevelSource.Repr[Long] = mappedWriter.mergeSubstreams

      mergedStream.runWith(Sink.onComplete { _ =>
        system.shutdown()
      })
  }
}
