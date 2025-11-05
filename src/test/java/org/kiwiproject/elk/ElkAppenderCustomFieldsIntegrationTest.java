package org.kiwiproject.elk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kiwiproject.collect.KiwiMaps;
import org.kiwiproject.elk.LogstashContainerExtension.LogstashContainerType;

import java.util.Map;

@DisplayName("ElkAppender (with custom fields)")
@Slf4j
class ElkAppenderCustomFieldsIntegrationTest extends AbstractElkAppenderIntegrationTest {

    @RegisterExtension
    static final LogstashContainerExtension LOGSTASH = LogstashContainerExtension.builder()
            .containerType(LogstashContainerType.SIMULATED)
            .customFields(Map.of(
                "application", "order-service",
                "environment", "dev"
            ))
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

    @Test
    void shouldHaveCustomFields() {
        var logger = dwApp().getIntegrationTestLogger();
        var italianHello = "Ciao da Dropwizard!";
        logger.info(italianHello);

        // Verify we saw the message
        logstash().awaitLogContains(italianHello);

         // Verify details of the log message
        var helloLog = logstash().findUniqueLogEntryContaining(italianHello);

         assertAll(
                () -> assertThat(KiwiMaps.getAsStringOrNull(helloLog, "application")).isEqualTo("order-service"),
                () -> assertThat(KiwiMaps.getAsStringOrNull(helloLog, "environment")).isEqualTo("dev")
         );
    }
}
