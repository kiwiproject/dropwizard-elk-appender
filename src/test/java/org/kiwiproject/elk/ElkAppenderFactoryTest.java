package org.kiwiproject.elk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AsyncAppenderBase;
import io.dropwizard.logging.async.AsyncLoggingEventAppenderFactory;
import io.dropwizard.logging.filter.ThresholdLevelFilterFactory;
import net.logstash.logback.appender.LogstashUdpSocketAppender;
import net.logstash.logback.layout.LogstashLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

@DisplayName("ElkAppenderFactory")
class ElkAppenderFactoryTest {
    private static final String APP_NAME = "aTestApp";

    @Test
    @SuppressWarnings("java:S5778")
    void shouldFail_WhenHostIsMissing() {
        var factory = new ElkAppenderFactory();
        assertThatThrownBy(() -> factory.build(new LoggerContext(),
                    APP_NAME,
                    null,
                    new ThresholdLevelFilterFactory(),
                    new AsyncLoggingEventAppenderFactory()))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("host must not be blank");
    }

    @Test
    @SuppressWarnings("java:S5778")
    void shouldFail_WhenPortIsMissing() {
        var factory = new ElkAppenderFactory();
        factory.setHost("localhost");

        assertThatThrownBy(() -> factory.build(new LoggerContext(),
                    APP_NAME,
                    null,
                    new ThresholdLevelFilterFactory(),
                    new AsyncLoggingEventAppenderFactory()))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("port must be greater than zero");
    }

    @Test
    void shouldCreateNewAppender() {
        var factory = new ElkAppenderFactory();
        factory.setHost("localhost");
        factory.setPort(9000);

        var appender = (AsyncAppenderBase<ILoggingEvent>) factory.build(new LoggerContext(),
                APP_NAME,
                null,
                new ThresholdLevelFilterFactory(),
                new AsyncLoggingEventAppenderFactory());

        assertThat(appender.getAppender("elk")).isInstanceOf(LogstashUdpSocketAppender.class);
    }

    @Test
    void shouldCreateNewAppender_WhenCustomFieldsAreProvided() {
        var factory = new ElkAppenderFactory();
        factory.setHost("localhost");
        factory.setPort(9000);
        factory.setCustomFields(Map.of("test", "@test"));

        var appender = (AsyncAppenderBase<ILoggingEvent>) factory.build(new LoggerContext(),
                APP_NAME,
                null,
                new ThresholdLevelFilterFactory(),
                new AsyncLoggingEventAppenderFactory());

        var elkAppender = (LogstashUdpSocketAppender) appender.getAppender("elk");
        var elkLayout = (LogstashLayout) elkAppender.getLayout();
        assertThat(elkLayout.getCustomFields()).contains("\"test\":\"@test\"");
    }

    @Test
    void shouldCreateNewAppender_WhenFieldNamesAreProvided() {
        var factory = new ElkAppenderFactory();
        factory.setHost("localhost");
        factory.setPort(9000);
        factory.setFieldNames(Map.of("timestamp", "123456"));

        var appender = (AsyncAppenderBase<ILoggingEvent>) factory.build(new LoggerContext(),
                APP_NAME,
                null,
                new ThresholdLevelFilterFactory(),
                new AsyncLoggingEventAppenderFactory());

        var elkAppender = (LogstashUdpSocketAppender) appender.getAppender("elk");
        var elkLayout = (LogstashLayout) elkAppender.getLayout();
        assertThat(elkLayout.getFieldNames().getTimestamp()).isEqualTo("123456");
    }
}
