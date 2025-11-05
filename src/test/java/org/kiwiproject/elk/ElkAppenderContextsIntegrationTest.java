package org.kiwiproject.elk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kiwiproject.base.UUIDs;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@DisplayName("ElkAppender (context and MDC)")
class ElkAppenderContextsIntegrationTest extends AbstractElkAppenderIntegrationTest {

    @RegisterExtension
    static final LogstashContainerExtension LOGSTASH = LogstashContainerExtension.builder()
            .containerType(LogstashContainerExtension.LogstashContainerType.SIMULATED)
            .build();

    @RegisterExtension
    static final DropwizardTestAppExtension DW_APP =
            new DropwizardTestAppExtension("elk-integration-test-config.yml");

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
    void shouldIncludeContextValues() {
        var logger = dwApp().getIntegrationTestLogger();
        var dutchHello = "Hallo van Dropwizard!";
        logger.info(dutchHello);

        // Verify we saw the message
        logstash().awaitLogContains(dutchHello);

        // Verify details of the log message
        var helloLog = logstash().findUniqueLogEntryContaining(dutchHello);

        assertAll(
                () -> assertThat(helloLog).containsEntry("organization", "kiwiproject"),
                () -> assertThat(helloLog).containsEntry("project", "dropwizard-elk-appender")
        );
    }

    @Test
    void shouldHaveMDCKeyValuePairs() {
        var traceId = UUIDs.randomUUIDString();
        MDC.put("traceId", traceId);

        var userId = "42";
        MDC.put("userId", userId);

        var logger = dwApp().getIntegrationTestLogger();
        var frenchHello = "Bonjour de la part de Dropwizard!";
        logger.info(frenchHello);

        // Verify we saw the message
        logstash().awaitLogContains(frenchHello);

        // Verify details of the log message
        var helloLog = logstash().findUniqueLogEntryContaining(frenchHello);

        assertAll(
                () -> assertThat(helloLog).containsEntry("traceId", traceId),
                () -> assertThat(helloLog).containsEntry("userId", userId)
        );
    }
}
