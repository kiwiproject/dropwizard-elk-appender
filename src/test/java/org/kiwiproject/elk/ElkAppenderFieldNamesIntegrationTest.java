package org.kiwiproject.elk;

import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.fieldnames.LogstashFieldNames;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kiwiproject.elk.LogstashContainerExtension.LogstashContainerType;

@DisplayName("ElkAppender (with custom logstash field names)")
@Slf4j
class ElkAppenderFieldNamesIntegrationTest extends AbstractElkAppenderIntegrationTest {

    @RegisterExtension
    static final LogstashContainerExtension LOGSTASH = LogstashContainerExtension.builder()
            .containerType(LogstashContainerType.SIMULATED)
            .build();

    @RegisterExtension
    static final DropwizardTestAppExtension DW_APP =
            new DropwizardTestAppExtension("elk-field-names-test-config.yml");

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
        fieldNames.setTimestamp("timestampEpochMillis");
        fieldNames.setVersion("ver");
        fieldNames.setMessage("logMessage");
        fieldNames.setLogger("logName");
        fieldNames.setThread("threadName");
        fieldNames.setLevel("logLevel");
        return fieldNames;
    }
}
