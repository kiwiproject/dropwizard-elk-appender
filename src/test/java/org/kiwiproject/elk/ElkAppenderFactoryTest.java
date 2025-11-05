package org.kiwiproject.elk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AsyncAppenderBase;
import io.dropwizard.logging.common.async.AsyncLoggingEventAppenderFactory;
import io.dropwizard.logging.common.filter.ThresholdLevelFilterFactory;
import net.logstash.logback.appender.LogstashTcpSocketAppender;
import net.logstash.logback.appender.LogstashUdpSocketAppender;
import net.logstash.logback.encoder.LogstashEncoder;
import net.logstash.logback.layout.LogstashLayout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junitpioneer.jupiter.SetSystemProperty;
import org.kiwiproject.config.provider.ResolvedBy;
import org.kiwiproject.elk.LogstashContainerExtension.LogstashContainerType;

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

    @Nested
    class BuildAppenderWithMissingData {

        @Test
        void shouldFail_WhenHostAndPortAreMissing() {
            assertThatThrownBy(this::tryBuildFactory)
                    .isExactlyInstanceOf(IllegalStateException.class)
                    .hasMessage("Unable to get ELK host and/or port from ElkLoggerConfigProvider." +
                            " Host resolution: %s, Port resolution: %s",
                            ResolvedBy.NONE.name(), ResolvedBy.NONE.name());
        }

        @Test
        @SetSystemProperty(key = "kiwi.elk.port", value = "9090")
        void shouldFail_WhenHostIsMissing() {
            assertThatThrownBy(this::tryBuildFactory)
                    .isExactlyInstanceOf(IllegalStateException.class)
                    .hasMessage("Unable to get ELK host and/or port from ElkLoggerConfigProvider." +
                            " Host resolution: %s, Port resolution: %s",
                            ResolvedBy.NONE.name(), ResolvedBy.SYSTEM_PROPERTY.name());
        }

        @Test
        @SetSystemProperty(key = "kiwi.elk.host", value = "localhost")
        void shouldFail_WhenPortIsMissing() {
            assertThatThrownBy(this::tryBuildFactory)
                    .isExactlyInstanceOf(IllegalStateException.class)
                    .hasMessage("Unable to get ELK host and/or port from ElkLoggerConfigProvider." +
                            " Host resolution: %s, Port resolution: %s",
                            ResolvedBy.SYSTEM_PROPERTY.name(), ResolvedBy.NONE.name());
        }

        private void tryBuildFactory() {
            var factory = new ElkAppenderFactory();
            factory.build(loggerContext,
                    APP_NAME,
                    null,
                    filterFactory,
                    appenderFactory);
        }
    }

    @Nested
    class BuildAppender {

        @RegisterExtension
        static final LogstashContainerExtension LOGSTASH = LogstashContainerExtension.builder()
                .containerType(LogstashContainerType.SIMULATED)
                .build();

        @Nested
        class UsingUdp {
            
            @Test
            void shouldCreateNewUdpAppender() {
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
            @SetSystemProperty(key = "kiwi.elk.customFields", value = "{\"test\":\"@test\"}")
            void shouldCreateNewUdpAppender_WhenCustomFieldsAreProvidedByProvider() {
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
        class UsingTcp {

            @Test
            void shouldCreateNewTcpAppender() {
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
}
