package org.kiwiproject.elk;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.kiwiproject.collect.KiwiMaps.isNotNullOrEmpty;
import static org.kiwiproject.collect.KiwiMaps.isNullOrEmpty;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.dropwizard.logging.common.AbstractAppenderFactory;
import io.dropwizard.logging.common.async.AsyncAppenderFactory;
import io.dropwizard.logging.common.filter.LevelFilterFactory;
import io.dropwizard.logging.common.layout.LayoutFactory;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.logstash.logback.appender.LogstashTcpSocketAppender;
import net.logstash.logback.appender.LogstashUdpSocketAppender;
import net.logstash.logback.encoder.LogstashEncoder;
import net.logstash.logback.fieldnames.LogstashFieldNames;
import net.logstash.logback.layout.LogstashLayout;
import org.kiwiproject.config.provider.ElkLoggerConfigProvider;
import org.kiwiproject.config.provider.ResolvedBy;
import org.kiwiproject.json.JsonHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * An implementation of {@link io.dropwizard.logging.common.AppenderFactory AppenderFactory}
 * that sends log messages to Logstash (the L in the ELK stack).
 * <p>
 * You don't use this directly, but instead configure it in your Dropwizard configuration file.
 * For example, here is a configuration that uses only default values:
 * <pre>
 * logging:
 *   level: WARN
 *   loggers:
 *     org.acme.service: INFO
 *   appenders:
 *     - type: elk
 * </pre>
 * And here's an example that uses UDP, doesn't include the logger context, and overrides
 * some of the default Logstash field names:
 * <pre>
 * logging:
 *   level: WARN
 *   loggers:
 *     org.acme.service: INFO
 *   appenders:
 *     - type: elk
 *       useUdp: true
 *       includeContext: false
 *       fieldNames:
 *         logger: loggerName
 *         thread: threadName
 *         level: logLevel
 * </pre>
 *  The available configuration properties are listed below.
 * <table>
 *     <caption>Configuration properties</caption>
 *     <tr>
 *         <td>Name</td>
 *         <td>Default</td>
 *         <td>Description</td>
 *     </tr>
 *     <tr>
 *         <td>{@code host}</td>
 *         <td>{@code null}</td>
 *         <td>
 *             The Logstash host. If not provided, then fall back to the value
 *             provided by {@link ElkLoggerConfigProvider}.
 *         </td>
 *     </tr>
 *     <tr>
 *         <td>{@code port}</td>
 *         <td>{@code null}</td>
 *         <td>
 *             The Logstash port. If not provided, then fall back to the value
 *             provided by {@link ElkLoggerConfigProvider}.
 *         </td>
 *     </tr>
 *     <tr>
 *         <td>{@code useUdp}</td>
 *         <td>{@code false}</td>
 *         <td>Whether to use UDP for connections to Logstash.</td>
 *     </tr>
 *     <tr>
 *         <td>{@code includeCallerData}</td>
 *         <td>{@code false}</td>
 *         <td>
 *             Whether to include caller data, required for line numbers.
 *             Beware, this is expensive as it creates a new Throwable on each logging call.
 *         </td>
 *     </tr>
 *     <tr>
 *         <td>{@code includeContext}</td>
 *         <td>{@code true}</td>
 *         <td>Whether to include the logging context in log messages.</td>
 *     </tr>
 *     <tr>
 *         <td>{@code includeMdc}</td>
 *         <td>{@code true}</td>
 *         <td>Whether to include the MDC (Message Diagnostic Context) in log messages.</td>
 *     </tr>
 *     <tr>
 *         <td>{@code customFields}</td>
 *         <td>empty map</td>
 *         <td>
 *             Custom fields that will be included in log messages.
 *             If not provided, then fall back to the value provided
 *             by {@link ElkLoggerConfigProvider}. Entries with blank
 *             keys or values are ignored when generating custom fields JSON.
 *         </td>
 *     </tr>
 *     <tr>
 *         <td>{@code fieldNames}</td>
 *         <td>empty map</td>
 *         <td>
 *             Map containing Logstash field name mappings.
 *             These override the default values in {@link net.logstash.logback.fieldnames.LogstashFieldNames LogstashFieldNames}.
 *         </td>
 *     </tr>
 * </table>
 *
 * Note that if either {@code host} or {@code port} is not specified, the fallback
 * {@link ElkLoggerConfigProvider} must be able to provide its value or an
 * {@code IllegalStateException} is thrown. For example, if {@code host} is set
 * on the factory, but {@code port} is not, then the {@link ElkLoggerConfigProvider}
 * must be able to provide the {@code port}.
 * <p>
 * If any {@code customFields} are specified on the factory, those values are used
 * and any value provided by {@link ElkLoggerConfigProvider} is ignored. In other words,
 * currently there is not a merge capability.
 */
@Setter
@Getter(AccessLevel.PACKAGE)  // getters are visible with package scope for testing
@JsonTypeName("elk")
public class ElkAppenderFactory extends AbstractAppenderFactory<ILoggingEvent> {

    private static final JsonHelper JSON_HELPER = JsonHelper.newDropwizardJsonHelper();
    private static final String ELK_PROVIDER_ERROR_MESSAGE_TEMPLATE =
            "Unable to get ELK host and/or port from ElkLoggerConfigProvider." +
                    " Host resolution: %s, Port resolution: %s";

    // NOTE: includeCallerData is handled by the inherited setter/getter from AbstractAppenderFactory

    private String host;
    private Integer port;
    private boolean useUdp;
    private boolean includeContext;
    private boolean includeMdc;
    private Map<String, String> customFields;
    private Map<String, String> fieldNames;
    @Setter(AccessLevel.NONE) private ElkLoggerConfigProvider elkLoggerConfigProvider;  // don't allow setting this

    /**
     * Create a new instance with default values.
     */
    public ElkAppenderFactory() {
        includeContext = true;
        includeMdc = true;
        customFields = new HashMap<>();
        fieldNames = new HashMap<>();
        elkLoggerConfigProvider = ElkLoggerConfigProvider.builder().build();
    }

    @Override
    public Appender<ILoggingEvent> build(LoggerContext loggerContext,
                                         String applicationName,
                                         LayoutFactory<ILoggingEvent> layoutFactory,
                                         LevelFilterFactory<ILoggingEvent> levelFilterFactory,
                                         AsyncAppenderFactory<ILoggingEvent> asyncAppenderFactory) {

        Map<String, ResolvedBy> resolvedBy = elkLoggerConfigProvider.getResolvedBy();
        var hostResolvedBy = resolvedBy.get("host");
        var portResolvedBy = resolvedBy.get("port");

        if (isBlank(host)) {
            checkState(canProvideHost(resolvedBy),
                    ELK_PROVIDER_ERROR_MESSAGE_TEMPLATE, hostResolvedBy, portResolvedBy);

            host = elkLoggerConfigProvider.getHost();
        }

        if (isNull(port)) {
            checkState(canProvidePort(resolvedBy),
                    ELK_PROVIDER_ERROR_MESSAGE_TEMPLATE, hostResolvedBy, portResolvedBy);

            port = elkLoggerConfigProvider.getPort();
        }

        var providerCustomFields = elkLoggerConfigProvider.getCustomFields();
        if (isNullOrEmpty(customFields) && nonNull(providerCustomFields)) {
            customFields = new HashMap<>(providerCustomFields);
        }

        var appender = createAppender();

        appender.setName("elk");
        appender.setContext(loggerContext);
        appender.addFilter(levelFilterFactory.build(threshold));
        appender.start();

        return wrapAsync(appender, asyncAppenderFactory);
    }

    private static boolean canProvideHost(Map<String, ResolvedBy> resolvedBy) {
        return canProvide(resolvedBy, "host");
    }

    private static boolean canProvidePort(Map<String, ResolvedBy> resolvedBy) {
        return canProvide(resolvedBy, "port");
    }

    private static boolean canProvide(Map<String, ResolvedBy> resolvedBy, String property) {
        return resolvedBy.get(property) != ResolvedBy.NONE;
    }

    private Appender<ILoggingEvent> createAppender() {
        checkState(port > 0 && port < 65536, "port %s is not a valid port (must be in range 1-65535)", port);

        return useUdp ? createUdpAppender() : createTcpAppender();
    }

    @SuppressWarnings("DuplicatedCode")
    private LogstashTcpSocketAppender createTcpAppender() {
        var encoder = new LogstashEncoder();
        encoder.setIncludeCallerData(isIncludeCallerData());
        encoder.setIncludeMdc(includeMdc);
        encoder.setIncludeContext(includeContext);

        if (isNotNullOrEmpty(customFields)) {
            getCustomFieldsAsJson().ifPresent(encoder::setCustomFields);
        }

        if (isNotNullOrEmpty(fieldNames)) {
            encoder.setFieldNames(getLogstashFieldNames());
        }

        var appender = new LogstashTcpSocketAppender();
        appender.addDestination(host + ":" + port);
        appender.setEncoder(encoder);

        return appender;
    }

    @SuppressWarnings("DuplicatedCode")
    private LogstashUdpSocketAppender createUdpAppender() {
        var layout = new LogstashLayout();
        layout.setIncludeCallerData(isIncludeCallerData());
        layout.setIncludeMdc(includeMdc);
        layout.setIncludeContext(includeContext);

        if (isNotNullOrEmpty(customFields)) {
            getCustomFieldsAsJson().ifPresent(layout::setCustomFields);
        }

        if (isNotNullOrEmpty(fieldNames)) {
            layout.setFieldNames(getLogstashFieldNames());
        }

        var appender = new LogstashUdpSocketAppender();
        appender.setHost(host);
        appender.setPort(port);
        appender.setLayout(layout);

        return appender;
    }

    private Optional<String> getCustomFieldsAsJson() {
        var filteredCustomFields = customFields.entrySet()
                .stream()
                .filter(entry -> isNotBlank(entry.getKey()) && isNotBlank(entry.getValue()))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

        return filteredCustomFields.isEmpty() ?
                Optional.empty() : Optional.of(JSON_HELPER.toJson(filteredCustomFields));
    }

    private LogstashFieldNames getLogstashFieldNames() {
        return ElkFieldHelper.getFieldNamesFromMap(fieldNames);
    }
}
