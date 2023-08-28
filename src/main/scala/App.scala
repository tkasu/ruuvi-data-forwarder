import zio.*

import java.io.{BufferedReader, InputStreamReader}

object App extends ZIOAppDefault:
  private def readNextLine(bufferedReader: BufferedReader) =
    for input <- ZIO.attempt(bufferedReader.readLine())
    yield input

  def run = for
    _ <- Console.printLine("Reading StdIn")
    reader = new InputStreamReader(java.lang.System.in)
    bufferedReader = new BufferedReader(reader)
    _ <- readNextLine(bufferedReader)
      .flatMap(nxt =>
        val hasNext = nxt != null && nxt != ""
        if hasNext then Console.printLine(nxt)
        else ZIO.succeed(hasNext)
      )
      .repeatUntil(_ == false)
    _ <- Console.printLine("Ending read")
  yield ()
