package org.kiwiproject.elk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.kiwiproject.test.constants.KiwiTestConstants.JSON_HELPER;

import ch.qos.logback.classic.LoggerContext;
import org.awaitility.Durations;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kiwiproject.base.UUIDs;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@DisplayName("ElkAppender (with no context or MDC)")
class ElkAppenderNoContextsIntegrationTest extends AbstractElkAppenderIntegrationTest {

    @RegisterExtension
    static final LogstashContainerExtension LOGSTASH = LogstashContainerExtension.builder()
            .containerType(LogstashContainerExtension.LogstashContainerType.SIMULATED)
            .build();

    @RegisterExtension
    static final DropwizardTestAppExtension DW_APP =
            new DropwizardTestAppExtension("elk-no-contexts-test-config.yml");

    @Override
    protected LogstashContainerExtension logstash() {
        return LOGSTASH;
    }

    @Override
    protected DropwizardTestAppExtension dwApp() {
        return DW_APP;
    }

    @BeforeAll
    static void beforeAll() {
        var context = getLoggerContext();
        context.putProperty("organization", "kiwiproject");
        context.putProperty("project", "dropwizard-elk-appender");
    }

    @AfterAll
    static void afterAll() {
        var context = getLoggerContext();
        context.putProperty("organization", null);
        context.putProperty("project", null);
    }

    private static LoggerContext getLoggerContext() {
        return (LoggerContext) LoggerFactory.getILoggerFactory();
    }

    @Test
    void shouldNotIncludeContextValues() {
        var logger = dwApp().getIntegrationTestLogger();
        logger.info("Hallo van Dropwizard!");

        // Verify we saw the message
        await().atMost(Durations.TEN_SECONDS)
                .untilAsserted(() -> assertThat(logstash().logs()).contains("Hallo van Dropwizard!"));

        // Verify details of the log message
        var helloLog = logstash().logs().lines()
                .filter(line -> line.contains("Hallo van Dropwizard!"))
                .map(JSON_HELPER::toMap)
                .findFirst()
                .orElseThrow();

        assertAll(
                () -> assertThat(helloLog).doesNotContainKey("organization"),
                () -> assertThat(helloLog).doesNotContainKey("project")
        );
    }

    @Test
    void shouldNotHaveMDCKeyValuePairs() {
        var traceId = UUIDs.randomUUIDString();
        MDC.put("traceId", traceId);

        var userId = "42";
        MDC.put("userId", userId);

        var logger = dwApp().getIntegrationTestLogger();
        logger.info("Bonjour de la part de Dropwizard!");

        // Verify we saw the message
        await().atMost(Durations.TEN_SECONDS)
                .untilAsserted(() -> assertThat(logstash().logs()).contains("Bonjour de la part de Dropwizard!"));

        // Verify details of the log message
        var helloLog = logstash().logs().lines()
                .filter(line -> line.contains("Bonjour de la part de Dropwizard!"))
                .map(JSON_HELPER::toMap)
                .findFirst()
                .orElseThrow();

        assertAll(
                () -> assertThat(helloLog).doesNotContainKey("traceId"),
                () -> assertThat(helloLog).doesNotContainKey("userId")
        );
    }
}
