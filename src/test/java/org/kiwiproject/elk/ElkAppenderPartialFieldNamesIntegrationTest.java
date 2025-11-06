package org.kiwiproject.elk;

import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.fieldnames.LogstashFieldNames;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kiwiproject.elk.LogstashContainerExtension.LogstashContainerType;

@DisplayName("ElkAppender (with some custom and some default logstash field names)")
@Slf4j
class ElkAppenderPartialFieldNamesIntegrationTest extends AbstractElkAppenderIntegrationTest {

    @RegisterExtension
    static final LogstashContainerExtension LOGSTASH = LogstashContainerExtension.builder()
            .containerType(LogstashContainerType.SIMULATED)
            .build();

    @RegisterExtension
    static final DropwizardTestAppExtension DW_APP =
            new DropwizardTestAppExtension("elk-partial-field-names-test-config.yml");

    @Override
    protected LogstashContainerExtension logstash() {
        return LOGSTASH;
    }

    @Override
    protected DropwizardTestAppExtension dwApp() {
        return DW_APP;
    }

    @Override
    protected LogstashFieldNames fieldNames() {
        var fieldNames = new LogstashFieldNames();
        // timestamp, version, and message field names have default values
        fieldNames.setLogger("logName");
        fieldNames.setThread("threadName");
        fieldNames.setLevel("logLevel");
        return fieldNames;
    }
}
