package org.kiwiproject.elk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@DisplayName("ElkAppender (caller data)")
class ElkAppenderCallerDataIntegrationTest extends AbstractElkAppenderIntegrationTest {

    @RegisterExtension
    static final LogstashContainerExtension LOGSTASH = LogstashContainerExtension.builder()
            .containerType(LogstashContainerExtension.LogstashContainerType.SIMULATED)
            .build();

    @RegisterExtension
    static final DropwizardTestAppExtension DW_APP =
            new DropwizardTestAppExtension("elk-caller-data-test-config.yml");

    @Override
    protected LogstashContainerExtension logstash() {
        return LOGSTASH;
    }

    @Override
    protected DropwizardTestAppExtension dwApp() {
        return DW_APP;
    }

    @Test
    void shouldIncludeCallerData() {
        var logger = dwApp().getIntegrationTestLogger();
        var danishHello = "Hej fra Dropwizard!";
        logger.info(danishHello);

        // Verify we saw the message
        logstash().awaitLogContains(danishHello);

        // Verify details of the log message
        var helloLog = logstash().findUniqueLogEntryContaining(danishHello);

        assertAll(
                () -> assertThat(helloLog).containsEntry("caller_class_name", ElkAppenderCallerDataIntegrationTest.class.getName()),
                () -> assertThat(helloLog).containsEntry("caller_method_name", "shouldIncludeCallerData"),
                () -> assertThat(helloLog).containsEntry("caller_file_name", ElkAppenderCallerDataIntegrationTest.class.getSimpleName() + ".java"),
                () -> assertThat(helloLog).containsKey("caller_line_number")
        );
    }
}
