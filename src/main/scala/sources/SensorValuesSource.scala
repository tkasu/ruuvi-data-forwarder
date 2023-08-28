package sources

import zio.stream.ZStream

trait SensorValuesSource:
  def make: ZStream[Any, Throwable, String]
