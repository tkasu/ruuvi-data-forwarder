package sources

import dto.RuuviTelemetry
import zio.stream.ZStream

trait SensorValuesSource:
  def make: ZStream[Any, SourceError, RuuviTelemetry]
