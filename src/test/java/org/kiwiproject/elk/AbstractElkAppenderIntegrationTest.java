package org.kiwiproject.elk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.kiwiproject.test.constants.KiwiTestConstants.JSON_HELPER;

import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.fieldnames.LogstashFieldNames;
import org.awaitility.Durations;
import org.junit.jupiter.api.Test;
import org.kiwiproject.collect.KiwiMaps;

import java.time.Instant;
import java.util.Map;

/**
 * Base class that provides tests for sending logs to Logstash.
 * <p>
 * Implementing classes must register their own {@link LogstashContainerExtension}
 * and {@link DropwizardTestAppExtension} expose them from the {@link #logstash()}
 * and {@link #dwApp()} methods, respectively. Since implementations must use extensions
 * declared as static, it cannot be done here.
 */
@Slf4j
abstract class AbstractElkAppenderIntegrationTest {

    /**
     * Implementing classes must override this to provide the LogstashContainerExtension.
     */
    protected abstract LogstashContainerExtension logstash();

    /**
     * Implementing classes must override this to provide the DropwizardTestAppExtension.
     */
    protected abstract DropwizardTestAppExtension dwApp();

    /**
     * Implementating classes may override to provide different field name mappings.
     * 
     * @see ElkAppenderFactory#setFieldNames(Map)
     */
    protected LogstashFieldNames fieldNames() {
        return new LogstashFieldNames();
    }

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
                .map(JSON_HELPER::toMap)
                .findFirst()
                .orElseThrow();

        var fieldNames = fieldNames();

        assertAll(
                () -> assertThat(KiwiMaps.getAsStringOrNull(helloLog, fieldNames.getTimestamp())).isNotBlank(),
                () -> assertThat(Instant.parse(KiwiMaps.getAsStringOrNull(helloLog, fieldNames.getTimestamp()))).isBefore(Instant.now()),
                () -> assertThat(KiwiMaps.getAsStringOrNull(helloLog, fieldNames.getLogger())).isEqualTo("integration-test"),
                () -> assertThat(KiwiMaps.getAsStringOrNull(helloLog, fieldNames.getLevel())).isEqualTo("INFO"),
                () -> assertThat(KiwiMaps.getAsStringOrNull(helloLog, fieldNames.getVersion())).isNotBlank(),
                () -> assertThat(KiwiMaps.getAsStringOrNull(helloLog, fieldNames.getThread())).isNotBlank()
        );

    }
}
