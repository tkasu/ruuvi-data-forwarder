package sinks

import dto.RuuviTelemetry
import zio.*
import zio.http.*
import zio.json.*
import zio.stream.*
import zio.logging.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class HttpSensorValuesSink(
    apiUrl: String,
    sensorName: String,
    debugLogging: Boolean,
    timeoutSeconds: Int = 30,
    maxRetries: Int = 3
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
        // Add error recovery to prevent stream crashes
        _ <- sendTelemetry(telemetry)
          .retry(Schedule.exponential(1.second) && Schedule.recurs(maxRetries))
          .catchAll { error =>
            ZIO.logError(
              s"Failed to send telemetry after ${maxRetries + 1} attempts: ${error.getMessage}"
            ) *> ZIO.unit // Continue processing next record
          }
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
      // URL encode sensor name and construct URL properly
      encodedSensorName = URLEncoder.encode(sensorName, "UTF-8")
      url = s"${apiUrl.stripSuffix("/")}/telemetry/$encodedSensorName"
      // Prepare body with proper headers
      bodyBytes = json.getBytes(StandardCharsets.UTF_8)
      maybeResponse <- Client
        .request(
          Request
            .post(url, Body.fromString(json))
            .addHeader(Header.ContentType(MediaType.application.json))
            .addHeader(Header.ContentLength(bodyBytes.length))
            // TODO: Add authentication header when ruuvitag-api implements auth
        )
        .timeout(timeoutSeconds.seconds)
        .provide(Client.default, Scope.default)
      response <- ZIO
        .fromOption(maybeResponse)
        .orElseFail(
          new RuntimeException(
            s"HTTP request timed out after ${timeoutSeconds}s"
          )
        )
      // Consume response body to prevent resource leaks
      _ <- response.body.asString
      _ <- response.status match
        case Status.Created =>
          ZIO.logInfo(s"Telemetry sent successfully to $url")
        case status =>
          ZIO.fail(
            new RuntimeException(
              s"HTTP request failed with status $status: ${response.status.text}"
            )
          )
    yield ()

  // Converts single RuuviTelemetry into 7 measurement types per ruuvitag-api spec:
  // temperature, humidity, pressure, battery, tx_power, movement_counter, measurement_sequence_number
  private[sinks] def convertToApiFormat(
      telemetry: RuuviTelemetry
  ): List[TelemetryData] =
    val timestamp = telemetry.measurementTsMs
    // Validate and format MAC address
    val macAddress =
      if telemetry.macAddress.length == 6 then
        telemetry.macAddress.map(b => f"${b & 0xff}%02X").mkString(":")
      else
        throw new IllegalArgumentException(
          s"Invalid MAC address length: ${telemetry.macAddress.length}, expected 6"
        )

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
