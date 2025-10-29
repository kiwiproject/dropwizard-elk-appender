package org.kiwiproject.elk;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

@EnabledIfSystemProperty(named = "enableHeavyIntegrationTests", matches = "true")
@DisplayName("ElkAppender (using Logstash in container)")
@Slf4j
class ElkAppenderFullIntegrationTest extends AbstractElkAppenderIntegrationTest {

    @RegisterExtension
    static final LogstashContainerExtension LOGSTASH = new LogstashContainerExtension();

    @RegisterExtension
    static final DropwizardTestAppExtension DW_APP = new DropwizardTestAppExtension();

    @Override
    protected LogstashContainerExtension logstash() {
        return LOGSTASH;
    }
}
