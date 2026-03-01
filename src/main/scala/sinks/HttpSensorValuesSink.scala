package sinks

import dto.RuuviTelemetry
import zio.*
import zio.http.*
import zio.json.*
import zio.stream.*
import zio.logging.*
import java.nio.charset.StandardCharsets

class HttpSensorValuesSink(
    apiUrl: String,
    debugLogging: Boolean,
    timeoutSeconds: Int = 30,
    maxRetries: Int = 3,
    val desiredBatchSize: Int = 1,
    val desiredMaxBatchLatencySeconds: Int = 5
) extends SensorValuesSink:

  // Data models for the ruuvitag-api request format
  case class Measurement(
      mac_address: String,
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

  def make: ZSink[Any, Throwable, Chunk[RuuviTelemetry], Nothing, Unit] =
    ZSink.foreach { chunk =>
      if chunk.isEmpty then ZIO.unit
      else
        for
          _ <- ZIO
            .logDebug(
              s"Sending batch of ${chunk.size} telemetries to HTTP API ($apiUrl)"
            )
            .when(debugLogging)
          // Send batch with proper error handling
          _ <- sendBatch(chunk.toList).catchAll { error =>
            // Always log and continue - never crash the stream
            ZIO.logError(
              s"Failed to send batch: ${error.getMessage}"
            ) *> ZIO.unit
          }
        yield ()
    }

  private def sendBatch(
      telemetries: List[RuuviTelemetry]
  ): ZIO[Any, Throwable, Unit] =
    val sendRequest = for
      // Convert all telemetries to API format and flatten into a single list
      allTelemetryData <- ZIO.succeed(
        telemetries.flatMap(convertToApiFormat)
      )
      // Group by telemetry_type and merge measurements
      groupedByType = allTelemetryData
        .groupBy(_.telemetry_type)
        .map { case (telemetryType, dataList) =>
          TelemetryData(
            telemetry_type = telemetryType,
            data = dataList.flatMap(_.data)
          )
        }
        .toList
      payload = groupedByType
      json <- ZIO.succeed(payload.toJson)
      _ <- ZIO
        .logDebug(s"Request payload (${telemetries.size} telemetries): $json")
        .when(debugLogging)
      // Construct URL for POST /telemetry endpoint
      url = s"${apiUrl.stripSuffix("/")}/telemetry"
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
      body <- response.body.asString
      _ <- response.status match
        case Status.Created =>
          ZIO.logInfo(
            s"Batch of ${telemetries.size} telemetries sent successfully to $url"
          )
        case status if status.isClientError =>
          // 4xx errors - client errors, don't retry, just log
          ZIO.logWarning(
            s"Client error sending batch to $url: ${status.code} ${response.status.text}. Response body: $body"
          )
        case status if status.isServerError =>
          // 5xx errors - server errors, should be retried
          ZIO.fail(
            new RuntimeException(
              s"Server error: ${status.code} ${response.status.text}"
            )
          )
        case status =>
          // Other unexpected status codes
          ZIO.logWarning(
            s"Unexpected status sending batch to $url: ${status.code} ${response.status.text}. Response body: $body"
          )
    yield ()

    // Retry only on server errors (5xx) and network/timeout errors
    sendRequest.retry(
      Schedule.exponential(1.second) && Schedule.recurs(maxRetries)
    )

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
            mac_address = macAddress,
            timestamp = timestamp,
            value = telemetry.temperatureMillicelsius / 1000.0
          )
        )
      ),
      TelemetryData(
        telemetry_type = "humidity",
        data = List(
          Measurement(
            mac_address = macAddress,
            timestamp = timestamp,
            value = telemetry.humidity / 10000.0
          )
        )
      ),
      TelemetryData(
        telemetry_type = "pressure",
        data = List(
          Measurement(
            mac_address = macAddress,
            timestamp = timestamp,
            value = telemetry.pressure.toDouble
          )
        )
      ),
      TelemetryData(
        telemetry_type = "battery",
        data = List(
          Measurement(
            mac_address = macAddress,
            timestamp = timestamp,
            value = telemetry.batteryPotential / 1000.0
          )
        )
      ),
      TelemetryData(
        telemetry_type = "tx_power",
        data = List(
          Measurement(
            mac_address = macAddress,
            timestamp = timestamp,
            value = telemetry.txPower.toDouble
          )
        )
      ),
      TelemetryData(
        telemetry_type = "movement_counter",
        data = List(
          Measurement(
            mac_address = macAddress,
            timestamp = timestamp,
            value = telemetry.movementCounter.toDouble
          )
        )
      ),
      TelemetryData(
        telemetry_type = "measurement_sequence_number",
        data = List(
          Measurement(
            mac_address = macAddress,
            timestamp = timestamp,
            value = telemetry.measurementSequenceNumber.toDouble
          )
        )
      )
    )
