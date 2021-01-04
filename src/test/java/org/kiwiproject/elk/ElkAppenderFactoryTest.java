package org.kiwiproject.elk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AsyncAppenderBase;
import io.dropwizard.logging.async.AsyncLoggingEventAppenderFactory;
import io.dropwizard.logging.filter.ThresholdLevelFilterFactory;
import net.logstash.logback.appender.LogstashTcpSocketAppender;
import net.logstash.logback.appender.LogstashUdpSocketAppender;
import net.logstash.logback.encoder.LogstashEncoder;
import net.logstash.logback.layout.LogstashLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

@DisplayName("ElkAppenderFactory")
class ElkAppenderFactoryTest {
    private static final String APP_NAME = "aTestApp";

    private LoggerContext loggerContext;
    private ThresholdLevelFilterFactory filterFactory;
    private AsyncLoggingEventAppenderFactory appenderFactory;

    @BeforeEach
    void setUp() {
        loggerContext = new LoggerContext();
        filterFactory = new ThresholdLevelFilterFactory();
        appenderFactory = new AsyncLoggingEventAppenderFactory();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("kiwi.elk.host");
        System.clearProperty("kiwi.elk.port");
        System.clearProperty("kiwi.elk.customFields");
    }

    @Test
    void shouldFail_WhenHostIsMissing() {
        var factory = new ElkAppenderFactory();
        assertThatThrownBy(() -> factory.build(loggerContext,
                    APP_NAME,
                    null,
                    filterFactory,
                    appenderFactory))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to find ELK host and port from ElkLoggerConfigProvider");
    }

    @Test
    void shouldFail_WhenPortIsMissing() {
        System.setProperty("kiwi.elk.host", "localhost");

        var factory = new ElkAppenderFactory();

        assertThatThrownBy(() -> factory.build(loggerContext,
                    APP_NAME,
                    null,
                    filterFactory,
                    appenderFactory))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to find ELK host and port from ElkLoggerConfigProvider");
    }

    @Nested
    class UseUdp {
        @Test
        void shouldCreateNewUdpAppender() {
            System.setProperty("kiwi.elk.host", "localhost");
            System.setProperty("kiwi.elk.port", "9000");

            var factory = new ElkAppenderFactory();
            factory.setUseUdp(true);

            var appender = (AsyncAppenderBase<ILoggingEvent>) factory.build(loggerContext,
                    APP_NAME,
                    null,
                    filterFactory,
                    appenderFactory);

            assertThat(appender.getAppender("elk")).isInstanceOf(LogstashUdpSocketAppender.class);
        }

        @Test
        void shouldCreateNewUdpAppender_WhenCustomFieldsAreProvided() {
            System.setProperty("kiwi.elk.host", "localhost");
            System.setProperty("kiwi.elk.port", "9000");

            var factory = new ElkAppenderFactory();
            factory.setUseUdp(true);
            factory.setCustomFields(Map.of("test", "@test"));

            var appender = (AsyncAppenderBase<ILoggingEvent>) factory.build(loggerContext,
                    APP_NAME,
                    null,
                    filterFactory,
                    appenderFactory);

            var elkAppender = (LogstashUdpSocketAppender) appender.getAppender("elk");
            var elkLayout = (LogstashLayout) elkAppender.getLayout();
            assertThat(elkLayout.getCustomFields()).contains("\"test\":\"@test\"");
        }

        @Test
        void shouldCreateNewUdpAppender_WhenCustomFieldsAreProvidedByProvider() {
            System.setProperty("kiwi.elk.host", "localhost");
            System.setProperty("kiwi.elk.port", "9000");
            System.setProperty("kiwi.elk.customFields", "{\"test\":\"@test\"}");

            var factory = new ElkAppenderFactory();
            factory.setUseUdp(true);

            var appender = (AsyncAppenderBase<ILoggingEvent>) factory.build(loggerContext,
                    APP_NAME,
                    null,
                    filterFactory,
                    appenderFactory);

            var elkAppender = (LogstashUdpSocketAppender) appender.getAppender("elk");
            var elkLayout = (LogstashLayout) elkAppender.getLayout();
            assertThat(elkLayout.getCustomFields()).contains("\"test\":\"@test\"");
        }

        @Test
        void shouldCreateNewUdpAppender_WhenFieldNamesAreProvided() {
            System.setProperty("kiwi.elk.host", "localhost");
            System.setProperty("kiwi.elk.port", "9000");

            var factory = new ElkAppenderFactory();
            factory.setUseUdp(true);
            factory.setFieldNames(Map.of("timestamp", "123456"));

            var appender = (AsyncAppenderBase<ILoggingEvent>) factory.build(loggerContext,
                    APP_NAME,
                    null,
                    filterFactory,
                    appenderFactory);

            var elkAppender = (LogstashUdpSocketAppender) appender.getAppender("elk");
            var elkLayout = (LogstashLayout) elkAppender.getLayout();
            assertThat(elkLayout.getFieldNames().getTimestamp()).isEqualTo("123456");
        }
    }

    @Nested
    class UseTcp {
        @Test
        void shouldCreateNewTcpAppender() {
            System.setProperty("kiwi.elk.host", "localhost");
            System.setProperty("kiwi.elk.port", "9000");

            var factory = new ElkAppenderFactory();

            var appender = (AsyncAppenderBase<ILoggingEvent>) factory.build(loggerContext,
                    APP_NAME,
                    null,
                    filterFactory,
                    appenderFactory);

            assertThat(appender.getAppender("elk")).isInstanceOf(LogstashTcpSocketAppender.class);
        }

        @Test
        void shouldCreateNewTcpAppender_WhenCustomFieldsAreProvided() {
            System.setProperty("kiwi.elk.host", "localhost");
            System.setProperty("kiwi.elk.port", "9000");

            var factory = new ElkAppenderFactory();
            factory.setCustomFields(Map.of("test", "@test"));

            var appender = (AsyncAppenderBase<ILoggingEvent>) factory.build(loggerContext,
                    APP_NAME,
                    null,
                    filterFactory,
                    appenderFactory);

            var elkAppender = (LogstashTcpSocketAppender) appender.getAppender("elk");
            var elkEncoder = (LogstashEncoder) elkAppender.getEncoder();
            assertThat(elkEncoder.getCustomFields()).contains("\"test\":\"@test\"");
        }

        @Test
        void shouldCreateNewTcpAppender_WhenFieldNamesAreProvided() {
            System.setProperty("kiwi.elk.host", "localhost");
            System.setProperty("kiwi.elk.port", "9000");

            var factory = new ElkAppenderFactory();
            factory.setFieldNames(Map.of("timestamp", "123456"));

            var appender = (AsyncAppenderBase<ILoggingEvent>) factory.build(loggerContext,
                    APP_NAME,
                    null,
                    filterFactory,
                    appenderFactory);

            var elkAppender = (LogstashTcpSocketAppender) appender.getAppender("elk");
            var elkEncoder = (LogstashEncoder) elkAppender.getEncoder();
            assertThat(elkEncoder.getFieldNames().getTimestamp()).isEqualTo("123456");
        }
    }

}
