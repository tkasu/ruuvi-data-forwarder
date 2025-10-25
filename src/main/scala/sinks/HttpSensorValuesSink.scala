package sinks

import dto.RuuviTelemetry
import zio.*
import zio.http.*
import zio.json.*
import zio.stream.*
import zio.logging.*

class HttpSensorValuesSink(
    apiUrl: String,
    sensorName: String,
    debugLogging: Boolean
) extends SensorValuesSink:

  // Data models for the ruuvitag-api request format
  case class Measurement(
      sensor_name: String,
      timestamp: Long,
      value: Double
  )

  object Measurement:
    implicit val encoder: JsonEncoder[Measurement] =
      DeriveJsonEncoder.gen[Measurement]

  case class TelemetryData(
      telemetry_type: String,
      data: List[Measurement]
  )

  object TelemetryData:
    implicit val encoder: JsonEncoder[TelemetryData] =
      DeriveJsonEncoder.gen[TelemetryData]

  def make: ZSink[Any, Throwable, RuuviTelemetry, Nothing, Unit] =
    ZSink.foreach { telemetry =>
      for
        _ <- ZIO
          .logDebug(
            s"Sending telemetry to HTTP API ($apiUrl): ${telemetry.macAddress.mkString(",")}"
          )
          .when(debugLogging)
        _ <- sendTelemetry(telemetry)
      yield ()
    }

  private def sendTelemetry(
      telemetry: RuuviTelemetry
  ): ZIO[Any, Throwable, Unit] =
    for
      payload <- ZIO.succeed(convertToApiFormat(telemetry))
      json <- ZIO.succeed(payload.toJson)
      _ <- ZIO
        .logDebug(s"Request payload: $json")
        .when(debugLogging)
      url = s"$apiUrl/telemetry/$sensorName"
      response <- Client
        .request(
          Request
            .post(url, Body.fromString(json))
            .addHeader(Header.ContentType(MediaType.application.json))
        )
        .provide(Client.default, Scope.default)
      _ <- response.status match
        case Status.Created =>
          ZIO.logDebug(s"Telemetry sent successfully to $url")
        case status =>
          ZIO.fail(
            new RuntimeException(
              s"HTTP request failed with status $status: ${response.status.text}"
            )
          )
    yield ()

  private def convertToApiFormat(
      telemetry: RuuviTelemetry
  ): List[TelemetryData] =
    val timestamp = telemetry.measurementTsMs
    val macAddress = telemetry.macAddress.map(b => f"${b & 0xff}%02X").mkString(":")

    List(
      TelemetryData(
        telemetry_type = "temperature",
        data = List(
          Measurement(
            sensor_name = macAddress,
            timestamp = timestamp,
            value = telemetry.temperatureMillicelsius / 1000.0
          )
        )
      ),
      TelemetryData(
        telemetry_type = "humidity",
        data = List(
          Measurement(
            sensor_name = macAddress,
            timestamp = timestamp,
            value = telemetry.humidity / 10000.0
          )
        )
      ),
      TelemetryData(
        telemetry_type = "pressure",
        data = List(
          Measurement(
            sensor_name = macAddress,
            timestamp = timestamp,
            value = telemetry.pressure.toDouble
          )
        )
      ),
      TelemetryData(
        telemetry_type = "battery",
        data = List(
          Measurement(
            sensor_name = macAddress,
            timestamp = timestamp,
            value = telemetry.batteryPotential / 1000.0
          )
        )
      ),
      TelemetryData(
        telemetry_type = "tx_power",
        data = List(
          Measurement(
            sensor_name = macAddress,
            timestamp = timestamp,
            value = telemetry.txPower.toDouble
          )
        )
      ),
      TelemetryData(
        telemetry_type = "movement_counter",
        data = List(
          Measurement(
            sensor_name = macAddress,
            timestamp = timestamp,
            value = telemetry.movementCounter.toDouble
          )
        )
      ),
      TelemetryData(
        telemetry_type = "measurement_sequence_number",
        data = List(
          Measurement(
            sensor_name = macAddress,
            timestamp = timestamp,
            value = telemetry.measurementSequenceNumber.toDouble
          )
        )
      )
    )
