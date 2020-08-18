### Dropwizard ELK Appender
[![Build Status](https://travis-ci.com/kiwiproject/dropwizard-elk-appender.svg?branch=master)](https://travis-ci.com/kiwiproject/dropwizard-elk-appender)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=kiwiproject_dropwizard-elk-appender&metric=alert_status)](https://sonarcloud.io/dashboard?id=kiwiproject_dropwizard-elk-appender)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=kiwiproject_dropwizard-elk-appender&metric=coverage)](https://sonarcloud.io/dashboard?id=kiwiproject_dropwizard-elk-appender)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

Dropwizard Logging Appender to send logging events to the ELK stack

#### How to use

Install library with Maven:

```xml
    <dependency>
        <groupId>org.kiwiproject</groupId>
        <artifactId>dropwizard-elk-appender</artifactId>
        <version>0.9.0</version>
    </dependency>
```

Add to Dropwizard config:

```yaml
    logging:
      level: WARN
      appenders:
        - type: elk
          host: localhost
          port: 9000
```

Properties that can be set in the config:

| Property Name     | Default | Description |
| --------------    | ------- | ----------- |
| host (required)   | blank   | Hostname for the logstash server |
| port (required)   | 0       | Port (greater than zero) for the logstash server |
| includeCallerData | false   | Whether the calling data gets included in the message to logstash |
| includeMdc        | true    | Whether to include the MDC in the message to logstash |
| includeContext    | true    | Whether to include the logging context in the message to logstash |
| customFields      | empty   | Map of custom fields that are to be sent to logstash for processing |
| fieldNames        | empty   | Map of field name mappings if overrides are needed |
