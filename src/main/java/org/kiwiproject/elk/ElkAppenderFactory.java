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

@Setter
@JsonTypeName("elk")
public class ElkAppenderFactory extends AbstractAppenderFactory<ILoggingEvent> {

    private static final JsonHelper JSON_HELPER = JsonHelper.newDropwizardJsonHelper();

    private boolean useUdp;
    private boolean includeCallerData;
    private boolean includeContext = true;
    private boolean includeMdc = true;
    private Map<String, String> customFields = new HashMap<>();
    private Map<String, String> fieldNames = new HashMap<>();

    private ElkLoggerConfigProvider elkLoggerConfigProvider = ElkLoggerConfigProvider.builder().build();

    @Override
    public Appender<ILoggingEvent> build(LoggerContext loggerContext,
                                         String s,
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
        encoder.setIncludeCallerData(includeCallerData);
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
        layout.setIncludeCallerData(includeCallerData);
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
