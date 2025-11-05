package org.kiwiproject.elk;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.RegisterExtension;

@EnabledForFullIntegrationTests
@DisplayName("ElkAppender TCP (using Logstash in container)")
@Slf4j
class ElkAppenderFullIntegrationTest extends AbstractElkAppenderIntegrationTest {

    @RegisterExtension
    static final LogstashContainerExtension LOGSTASH = new LogstashContainerExtension();

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
}
