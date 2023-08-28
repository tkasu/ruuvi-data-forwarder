package sources

import zio.*
import zio.stream.*

object ConsoleSensorValuesSource extends SensorValuesSource:
  def make: ZStream[Any, Throwable, String] =
    ZStream.fromZIO(Console.readLine)
