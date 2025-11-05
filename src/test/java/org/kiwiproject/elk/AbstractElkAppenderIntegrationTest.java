package org.kiwiproject.elk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.kiwiproject.test.constants.KiwiTestConstants.JSON_HELPER;

import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.fieldnames.LogstashFieldNames;
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
        var spanishHello = "¡Hola mensaje de Dropwizard!";
        logger.info(spanishHello);

        // Verify we saw the message TEMPORARY FOR SONAR DEBUGGING...AGAIN
        logstash().awaitLogContains(spanishHello);
    }

    @Test
    void shouldSendLogs() {
        var logger = dwApp().getIntegrationTestLogger();
        
        var debugHello = "Debugging message from Dropwizard!";
        var infoHello = "Hello message from Dropwizard!";
        var warnHello = "Warning message from Dropwizard!";
        var errorHello = "Error message from Dropwizard!";
        
        logger.debug(debugHello);
        logger.info(infoHello);
        logger.warn(warnHello);
        logger.error(errorHello);

        // Verify we saw all the expected messages
        logstash().awaitLogContains(debugHello, infoHello, warnHello, errorHello);
    }

    @Test
    void shouldGetLogsInExpectedFormat() {
        var logger = dwApp().getIntegrationTestLogger();
        var germanHello = "Hallo Nachricht von Dropwizard!";
        logger.info(germanHello);

        // Verify we saw the message
        logstash().awaitLogContains(germanHello);

        // Verify details of the log message
        var helloLog = logstash().logs().lines()
                .filter(line -> line.contains(germanHello))
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
