## Ruuvi data forwarder

Note! This program is heavily work in progress and does not do anything useful yet.

This program is utility with the end goal of forwarding data from Ruuvi to different targets, e.g. to HTTP endpoint, S3 or Database.

### Requirements

* jdk (tested with 21)
* sbt

### Development

#### Tests

```shell
sbt scalafmtCheckAll test
```

#### Build

```shell
sbt assembly
```

### Usage

Test input:

```shell
echo '{"battery_potential":2335,"humidity":653675,"measurement_ts_ms":1693460525701,"mac_address":[254,38,136,122,102,102],"measurement_sequence_number":53300,"movement_counter":2,"pressure":100755,"temperature_millicelsius":-29020,"tx_power":4}' | java -jar target/scala-3.*/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
```

With [ruuvi-reader-rs](https://github.com/tkasu/ruuvi-reader-rs)

```shell
ruuvi-reader-rs | java -jar target/scala-3.*/ruuvi-data-forwarder-assembly-0.1.0-SNAPSHOT.jar
```
