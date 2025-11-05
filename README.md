# Dropwizard ELK Appender

[![Build](https://github.com/kiwiproject/dropwizard-elk-appender/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/kiwiproject/dropwizard-elk-appender/actions/workflows/build.yml?query=branch%3Amain)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=kiwiproject_dropwizard-elk-appender&metric=alert_status)](https://sonarcloud.io/dashboard?id=kiwiproject_dropwizard-elk-appender)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=kiwiproject_dropwizard-elk-appender&metric=coverage)](https://sonarcloud.io/dashboard?id=kiwiproject_dropwizard-elk-appender)
[![CodeQL](https://github.com/kiwiproject/dropwizard-elk-appender/actions/workflows/codeql.yml/badge.svg)](https://github.com/kiwiproject/dropwizard-elk-appender/actions/workflows/codeql.yml)
[![javadoc](https://javadoc.io/badge2/org.kiwiproject/dropwizard-elk-appender/javadoc.svg)](https://javadoc.io/doc/org.kiwiproject/dropwizard-elk-appender)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Maven Central](https://img.shields.io/maven-central/v/org.kiwiproject/dropwizard-elk-appender)](https://central.sonatype.com/artifact/org.kiwiproject/dropwizard-elk-appender/)

Dropwizard Logging Appender to send logging events to the ELK stack.

## Installation

Install library with Maven:

```xml
    <dependency>
        <groupId>org.kiwiproject</groupId>
        <artifactId>dropwizard-elk-appender</artifactId>
        <version>[current-version]</version>
    </dependency>
```

## Running tests

The tests use [Testcontainers](https://testcontainers.com/) and require Docker.

Run tests as usual:

```bash
mvn test
```

To run the full integration tests, which start an actual Logstash server,
you can do:

```bash
mvn test -DfullIntegrationTests
```

Note that it can take a while (20+ seconds) to start the Logstash server container.

## How to use in an application

Add to your Dropwizard configuration:

```yaml
    logging:
      level: WARN
      appenders:
        - type: elk
```

The connection details of the Logstash server must be set, _but this cannot be done
directly in the Dropwizard configuration._

To set the host and port of the Logstash server, as well as custom fields, you can use system
properties, environment variables, or a configuration file as defined by
[ElkLoggerConfigProvider](https://javadoc.io/doc/org.kiwiproject/dropwizard-config-providers/latest/org/kiwiproject/config/provider/ElkLoggerConfigProvider.html).

The custom fields must be JSON, for example:

```json
{ "serviceName": "invoice-service", "serviceHost": "dev-svc-1.acme.com", "serviceEnvironment": "dev" }
```

As an example, if you wanted to use environment variables you could do it like this in a script:

```bash
# export the environment variables
export KIWI_ELK_HOST=dev-logstash-1.acme.com
export KIWI_ELK_PORT=7003
export KIWI_ELK_CUSTOM_FIELDS='{ "serviceName": "invoice-service", "serviceHost": "dev-svc-1.acme.com", "serviceEnvironment": "dev" }'

# start the Dropwizard application
java -jar /opt/service/invoice-service/service.jar server /opt/service/invoice-service/config.yml
```

## Custom configuration

The properties that can be set in the Dropwizard configuration are:

| Property Name     | Default | Description                                                         |
|-------------------|---------|---------------------------------------------------------------------|
| includeCallerData | false   | Whether the caller data is included in the message to logstash      |
| includeContext    | true    | Whether to include the logging context in the message to logstash   |
| includeMdc        | true    | Whether to include the MDC in the message to logstash               |
| fieldNames        | empty   | Map of Logstash field name mappings if overrides are needed         |
| useUdp            | false   | Whether to use UDP instead of the default TCP for connections       |

Below is a custom configuration that does not include the [logging context](https://logback.qos.ch/manual/architecture.html#LoggerContext)
or the [MDC](https://logback.qos.ch/manual/mdc.html).

It also overrides the default Logstash field names, whose default values are defined by the
[LogstashCommonFieldNames](https://javadoc.io/doc/net.logstash.logback/logstash-logback-encoder/latest/logstash.logback.encoder/net/logstash/logback/fieldnames/LogstashCommonFieldNames.html)
and [LogstashFieldNames](https://javadoc.io/doc/net.logstash.logback/logstash-logback-encoder/latest/logstash.logback.encoder/net/logstash/logback/fieldnames/LogstashFieldNames.html)
classes in the [logstash-logback-encoder](https://github.com/logfellow/logstash-logback-encoder) library.

```yaml
    logging:
      level: WARN
      appenders:
        - type: elk
          includeContext: false
          includeMdc: false
          fieldNames:
            timestamp: timestampEpochMillis
            version: ver
            message: logMessage
            logger: logName
            thread: threadName
            level: logLevel
```

### Caller Data

It might be tempting to set `includeCallerData` to `true`, which will then include information
in every log message about the caller: `caller_class_name`, `caller_method_name`, `caller_file_name`,
and `caller_line_number`. But including that data is ["rather expensive"](https://logback.qos.ch/manual/appenders.html#asyncIncludeCallerData)
and should usually only be done in non-production environments. The reason is it expensive is that
a new `Throwable` is constructed for every log message. See the implementation in
[LoggingEvent#getCallerData](https://github.com/qos-ch/logback/blob/1cd2df4be866ba48ec410ecd2e33855324b62476/logback-classic/src/main/java/ch/qos/logback/classic/spi/LoggingEvent.java#L399)
if you want the gory details.

But if you really want to include it, just set `includeCallerData` to `true` in your configuration:

```yaml
    logging:
      level: WARN
      appenders:
        - type: elk
          includeCallerData: true
```
