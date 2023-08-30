package dto

import zio.json.*

@jsonMemberNames(SnakeCase)
case class RuuviTelemetry(
    temperatureMillicelsius: Int,
    humidity: Int,
    pressure: Int,
    batteryPotential: Int,
    txPower: Int,
    movementCounter: Int,
    measurementSequenceNumber: Int,
    macAddress: Seq[Short]
)

object RuuviTelemetry:
  implicit val decoder: JsonDecoder[RuuviTelemetry] =
    DeriveJsonDecoder.gen[RuuviTelemetry]
  implicit val encoder: JsonEncoder[RuuviTelemetry] =
    DeriveJsonEncoder.gen[RuuviTelemetry]
