package org.kiwiproject.elk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.kiwiproject.test.assertj.KiwiAssertJ.assertIsExactType;
import static org.kiwiproject.test.constants.KiwiTestConstants.JSON_HELPER;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junitpioneer.jupiter.ClearSystemProperty;
import org.junitpioneer.jupiter.SetSystemProperty;
import org.kiwiproject.collect.KiwiMaps;
import org.kiwiproject.config.provider.ResolvedBy;
import org.kiwiproject.elk.LogstashContainerExtension.LogstashContainerType;

import java.net.InetSocketAddress;
import java.util.List;
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
    class Constructor {

        @Test
        void shouldSetDefaultValues() {
            var factory = new ElkAppenderFactory();

            assertAll(
                () -> assertThat(factory.isUseUdp()).isFalse(),
                () -> assertThat(factory.isIncludeCallerData()).isFalse(),
                () -> assertThat(factory.isIncludeContext()).isTrue(),
                () -> assertThat(factory.isIncludeMdc()).isTrue(),
                () -> assertThat(factory.getCustomFields()).isEmpty(),
                () -> assertThat(factory.getFieldNames()).isEmpty(),
                () -> assertThat(factory.getElkLoggerConfigProvider()).isNotNull()
            );
        }
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
    @SetSystemProperty(key = "kiwi.elk.host", value = "localhost")
    @SetSystemProperty(key = "kiwi.elk.port", value = "5044")
    @SetSystemProperty(
            key = "kiwi.elk.customFields",
            value = """
                    {
                        "serviceName": "default-service",
                        "serviceHost": "localhost",
                        "serviceEnvironment": "local"
                    }
                    """
    )
    class WithSettingsOnFactory {

        @Test
        void shouldUseHostAndPortFromFactory() {
            var factory = new ElkAppenderFactory();
            var host = "dev-log-1.example.com";
            var port = 57_003;
            factory.setHost(host);
            factory.setPort(port);

            var appender = factory.build(loggerContext, APP_NAME, null, filterFactory, appenderFactory);

            var destinations = getLogstashAppenderDestinations(appender);
            assertThat(destinations)
                    .describedAs("should use host and port from ElkAppenderFactory")
                    .extracting(InetSocketAddress::getHostName, InetSocketAddress::getPort)
                    .containsExactly(tuple(host, port));
        }

        @Test
        void shouldUseHostFromFactory_AndFallbackToProviderForPort() {
            var factory = new ElkAppenderFactory();
            var host = "dev-log-1.example.com";
            factory.setHost(host);

            var appender = factory.build(loggerContext, APP_NAME, null, filterFactory, appenderFactory);

            var destinations = getLogstashAppenderDestinations(appender);
            assertThat(destinations)
                    .describedAs("should use host from ElkAppenderFactory, port from system property")
                    .extracting(InetSocketAddress::getHostName, InetSocketAddress::getPort)
                    .containsExactly(tuple(host, 5044));
        }

        @Test
        void shouldUsePortFromFactory_AndFallbackToProvidedHost() {
            var factory = new ElkAppenderFactory();
            var port = 37_003;
            factory.setPort(port);

            var appender = factory.build(loggerContext, APP_NAME, null, filterFactory, appenderFactory);

            var destinations = getLogstashAppenderDestinations(appender);
            assertThat(destinations)
                    .describedAs("should use host from system property, port from ElkAppenderFactory")
                    .extracting(InetSocketAddress::getHostName, InetSocketAddress::getPort)
                    .containsExactly(tuple("localhost", port));
        }

        @Test
        void shouldUseCustomFields_FromFactory() {
            var factory = new ElkAppenderFactory();
            var customFields = Map.of(
                    "serviceName", "invoice-service",
                    "serviceEnvironment", "dev"
            );
            factory.setCustomFields(customFields);

            var appender = factory.build(loggerContext, APP_NAME, null, filterFactory, appenderFactory);

            var encoderCustomFields = extractEncoderCustomFields(appender);
            assertThat(encoderCustomFields).isEqualTo(customFields);
        }

        @ParameterizedTest
        @NullAndEmptySource
        void shouldHandleNullOrEmptyCustomFieldsOnFactory_AndFallbackToProvidedValues(Map<String, String> customFields) {
            var factory = new ElkAppenderFactory();
            factory.setCustomFields(customFields);

            var appender = factory.build(loggerContext, APP_NAME, null, filterFactory, appenderFactory);

            var encoderCustomFields = extractEncoderCustomFields(appender);
            var providerCustomFields = JSON_HELPER.toMap(System.getProperty("kiwi.elk.customFields"));
            assertThat(encoderCustomFields).isEqualTo(providerCustomFields);
        }

        @ParameterizedTest
        @CsvSource(textBlock = """
                serviceEnvironment, null
                serviceEnvironment, ''
                null, dev
                '', dev
                """, nullValues = "null")
        void shouldFilterOutCustomFieldEntries_HavingBlankKeyOrValue(String key, String value) {
            var customFields = KiwiMaps.<String, String>newHashMap(
                    "serviceName", "test-service",
                    "serviceVersion", "1.0.0",
                    key, value
            );

            var factory = new ElkAppenderFactory();
            factory.setCustomFields(customFields);

            var appender = factory.build(loggerContext, APP_NAME, null, filterFactory, appenderFactory);

            var encoderCustomFields = extractEncoderCustomFields(appender);
            assertThat(encoderCustomFields).containsOnly(
                    entry("serviceName", "test-service"),
                    entry("serviceVersion", "1.0.0")
            );
        }

        @Test
        void shouldNotSetCustomFields_WhenAllAreFilteredOut() {
            var customFields = KiwiMaps.<String, String>newHashMap(
                    "serviceName", null,
                    "serviceVersion", "",
                    "serviceEnvironment", " "
            );

            var factory = new ElkAppenderFactory();
            factory.setCustomFields(customFields);

            var appender = factory.build(loggerContext, APP_NAME, null, filterFactory, appenderFactory);

            var encoderCustomFields = extractEncoderCustomFields(appender);
            assertThat(encoderCustomFields)
                    .describedAs("Custom fields should not be set when all are filtered out")
                    .isNull();
        }

        @Test
        @ClearSystemProperty(key = "kiwi.elk.customFields")
            // note: clearing the property set above
        void shouldIgnoreNullCustomFieldsOnFactory_WhenCustomFieldsNotProvided() {
            var factory = new ElkAppenderFactory();
            factory.setCustomFields(null);

            var appender = factory.build(loggerContext, APP_NAME, null, filterFactory, appenderFactory);

            var encoderCustomFields = extractEncoderCustomFields(appender);
            assertThat(encoderCustomFields).isNull();
        }

        private static Map<String, Object> extractEncoderCustomFields(Appender<ILoggingEvent> appender) {
            var logstashTcpAppender = getLogstashTcpSocketAppender(appender);
            var logstashEncoder = assertIsExactType(logstashTcpAppender.getEncoder(), LogstashEncoder.class);

            var customFieldsJson = logstashEncoder.getCustomFields();
            return JSON_HELPER.toMap(customFieldsJson);
        }

        @ParameterizedTest
        @ValueSource(ints = { -2, -1, 0, 65_536 })
        void shouldThrowIllegalState_WhenPortIsInvalid(int port) {
            var factory = new ElkAppenderFactory();
            factory.setPort(port);

            assertThatIllegalStateException()
                    .isThrownBy(() -> factory.build(loggerContext, APP_NAME, null, filterFactory, appenderFactory))
                    .withMessage("port %d is not a valid port (must be in range 1-65535)", port);
        }

        private static List<InetSocketAddress> getLogstashAppenderDestinations(Appender<ILoggingEvent> appender) {
            var logstashTcpAppender = getLogstashTcpSocketAppender(appender);
            return logstashTcpAppender.getDestinations();
        }

        private static LogstashTcpSocketAppender getLogstashTcpSocketAppender(Appender<ILoggingEvent> appender) {
            var asyncAppender = assertIsExactType(appender, AsyncAppender.class);
            var loggingEventAppender = asyncAppender.iteratorForAppenders().next();
            return assertIsExactType(loggingEventAppender, LogstashTcpSocketAppender.class);
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
