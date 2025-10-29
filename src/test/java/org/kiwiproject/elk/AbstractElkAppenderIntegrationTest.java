package org.kiwiproject.elk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.kiwiproject.test.constants.KiwiTestConstants.JSON_HELPER;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.awaitility.Durations;
import org.junit.jupiter.api.Test;

import java.time.Instant;

/**
 * Base class that provides tests for sending logs to Logstash.
 * <p>
 * Implementing classes must register their own {@link LogstashContainerExtension}
 * and {@link DropwizardTestAppExtension} expose them from the {@link #logstash()}
 * and {@link #dwApp()} methods, respectively. Since implementations must use extensions
 * declared as static, it cannot be done here.
 */
abstract class AbstractElkAppenderIntegrationTest {

    /**
     * Implementing classes must override this to provide the LogstashContainerExtension.
     */
    protected abstract LogstashContainerExtension logstash();

    /**
     * Implementing classes must override this to provide the DropwizardTestAppExtension.
     */
    protected abstract DropwizardTestAppExtension dwApp();

    @Test
    void shouldSendLog() {
        var logger = dwApp().getIntegrationTestLogger();
        logger.info("¡Hola mensaje de Dropwizard!");

        // Verify we saw the message
        await().atMost(Durations.TEN_SECONDS)
                .untilAsserted(() -> assertThat(logstash().logs()).contains("¡Hola mensaje de Dropwizard!"));
    }

    @Test
    void shouldSendLogs() {
        var logger = dwApp().getIntegrationTestLogger();
        logger.debug("Debugging message from Dropwizard!");
        logger.info("Hello message from Dropwizard!");
        logger.warn("Warning message from Dropwizard!");
        logger.error("Error message from Dropwizard!");

        // Verify we saw all the expected messages
        await().atMost(Durations.TEN_SECONDS)
                .untilAsserted(() -> assertThat(logstash().logs())
                        .contains("Debugging message from Dropwizard!")
                        .contains("Hello message from Dropwizard!")
                        .contains("Warning message from Dropwizard!")
                        .contains("Error message from Dropwizard!"));
    }

    /**
     * Record that represents the most basic information expected in a Logstash record.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record LogstashLogRecord(
            @JsonProperty("@timestamp") String timestamp,
            @JsonProperty("message") String message,
            @JsonProperty("logger_name") String loggerName,
            @JsonProperty("level") String level,
            @JsonProperty("@version") String version,
            @JsonProperty("thread_name") String threadName) {
    }

    @Test
    void shouldGetLogsInExpectedFormat() {
        var logger = dwApp().getIntegrationTestLogger();
        logger.info("Hallo Nachricht von Dropwizard!");

         // Verify we saw the message
        await().atMost(Durations.TEN_SECONDS)
                .untilAsserted(() -> assertThat(logstash().logs()).contains("Hallo Nachricht von Dropwizard!"));

        // Verify details of the log message
        var helloLog = logstash().logs().lines()
                .filter(line -> line.contains("Hallo Nachricht von Dropwizard!"))
                .map(line -> JSON_HELPER.toObject(line, LogstashLogRecord.class))
                .findFirst()
                .orElseThrow();

        assertAll(
                () -> assertThat(helloLog.timestamp()).isNotBlank(),
                () -> assertThat(Instant.parse(helloLog.timestamp())).isBefore(Instant.now()),
                () -> assertThat(helloLog.loggerName()).isEqualTo("integration-test"),
                () -> assertThat(helloLog.level()).isEqualTo("INFO"),
                () -> assertThat(helloLog.version()).isNotBlank(),
                () -> assertThat(helloLog.threadName()).isNotBlank());
    }
}
