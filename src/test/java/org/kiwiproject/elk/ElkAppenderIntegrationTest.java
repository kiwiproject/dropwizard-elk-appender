package org.kiwiproject.elk;

import static org.assertj.core.api.Assertions.assertThat;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kiwiproject.elk.LogstashContainerExtension.LogstashContainerType;

@DisplayName("ElkAppender (using simulated Logstash)")
@Slf4j
class ElkAppenderIntegrationTest extends AbstractElkAppenderIntegrationTest {

    @RegisterExtension
    static final LogstashContainerExtension LOGSTASH = LogstashContainerExtension.builder()
            .containerType(LogstashContainerType.SIMULATED)
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
    void shouldNotIncludeCallerDataByDefault() {
        var logger = dwApp().getIntegrationTestLogger();
        var klingonHello = "nuqneH Dropwizard!";
        logger.info(klingonHello);

        // Verify we saw the message
        logstash().awaitLogContains(klingonHello);

        // Verify details of the log message
        var helloLog = logstash().findUniqueLogEntryContaining(klingonHello);

        System.out.println("helloLog = " + helloLog);

        assertThat(helloLog).doesNotContainKeys(
                "caller_class_name",
                "caller_method_name",
                "caller_file_name",
                "caller_line_number"
        );
    }
}
