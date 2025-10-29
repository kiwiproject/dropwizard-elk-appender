package org.kiwiproject.elk;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kiwiproject.elk.LogstashContainerExtension.LogstashContainerType;

@DisplayName("ElkAppender (using simulated Logstash)")
@Slf4j
class ElkAppenderLightweightIntegrationTest extends AbstractElkAppenderIntegrationTest {

    @RegisterExtension
    static final LogstashContainerExtension LOGSTASH =
            new LogstashContainerExtension(LogstashContainerType.SIMULATED);

    @RegisterExtension
    static final DropwizardTestAppExtension DW_APP = new DropwizardTestAppExtension();

    @Override
    protected LogstashContainerExtension logstash() {
        return LOGSTASH;
    }
}
