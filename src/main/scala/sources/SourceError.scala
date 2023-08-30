package sources

sealed trait SourceError extends Exception

case class RuuviParseError(
    private val message: String = "",
    private val cause: Throwable = None.orNull
) extends Exception(message, cause) with SourceError

case class StreamShutdown() extends Exception with SourceError