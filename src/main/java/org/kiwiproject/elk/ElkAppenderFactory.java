package org.kiwiproject.elk;

import static com.google.common.base.Preconditions.checkState;
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
import net.logstash.logback.layout.LogstashLayout;
import org.kiwiproject.config.provider.ElkLoggerConfigProvider;
import org.kiwiproject.config.provider.ResolvedBy;
import org.kiwiproject.json.JsonHelper;

import java.util.HashMap;
import java.util.Map;

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
 *         <td>Custom fields that will be included in log messages.</td>
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
 */
@Setter
@Getter(AccessLevel.PACKAGE)  // getters are visible with package scope for testing
@JsonTypeName("elk")
public class ElkAppenderFactory extends AbstractAppenderFactory<ILoggingEvent> {

    private static final JsonHelper JSON_HELPER = JsonHelper.newDropwizardJsonHelper();

    // NOTE: includeCallerData is handled by the inherited setter/getter from AbstractAppenderFactory
    
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
        checkState(elkLoggerConfigProvider.canProvide(),
                "Unable to get ELK host and/or port from ElkLoggerConfigProvider." +
                " Host resolution: %s, Port resolution: %s",
                resolvedBy.get("host"), resolvedBy.get("port"));

        if (isNullOrEmpty(customFields)) {
            customFields = elkLoggerConfigProvider.getCustomFields();
        }

        var appender = useUdp ? createUdpAppender() : createTcpAppender();

        appender.setName("elk");
        appender.setContext(loggerContext);
        appender.addFilter(levelFilterFactory.build(threshold));
        appender.start();

        return wrapAsync(appender, asyncAppenderFactory);
    }

    @SuppressWarnings("DuplicatedCode")
    private LogstashTcpSocketAppender createTcpAppender() {
        var encoder = new LogstashEncoder();
        encoder.setIncludeCallerData(isIncludeCallerData());
        encoder.setIncludeMdc(includeMdc);
        encoder.setIncludeContext(includeContext);

        if (isNotNullOrEmpty(customFields)) {
            encoder.setCustomFields(JSON_HELPER.toJson(customFields));
        }

        if (isNotNullOrEmpty(fieldNames)) {
            encoder.setFieldNames(ElkFieldHelper.getFieldNamesFromMap(fieldNames));
        }

        var appender = new LogstashTcpSocketAppender();
        appender.addDestination(elkLoggerConfigProvider.getHost() + ":" + elkLoggerConfigProvider.getPort());
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
            layout.setCustomFields(JSON_HELPER.toJson(customFields));
        }

        if (isNotNullOrEmpty(fieldNames)) {
            layout.setFieldNames(ElkFieldHelper.getFieldNamesFromMap(fieldNames));
        }

        var appender = new LogstashUdpSocketAppender();
        appender.setHost(elkLoggerConfigProvider.getHost());
        appender.setPort(elkLoggerConfigProvider.getPort());
        appender.setLayout(layout);

        return appender;
    }
}
