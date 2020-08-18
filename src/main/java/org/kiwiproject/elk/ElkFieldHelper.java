package org.kiwiproject.elk;

import lombok.experimental.UtilityClass;
import net.logstash.logback.fieldnames.LogstashFieldNames;

import java.util.Map;

@UtilityClass
class ElkFieldHelper {

    LogstashFieldNames getFieldNamesFromMap(Map<String, String> map) {
        var fieldNames = new LogstashFieldNames();

        fieldNames.setTimestamp(map.getOrDefault("timestamp", "@timestamp"));
        fieldNames.setVersion(map.getOrDefault("version", "@version"));
        fieldNames.setMessage(map.getOrDefault("message", "message"));
        fieldNames.setLogger(map.getOrDefault("logger", "logger_name"));
        fieldNames.setThread(map.getOrDefault("thread", "thread_name"));
        fieldNames.setLevel(map.getOrDefault("level", "level"));
        fieldNames.setCaller(map.getOrDefault("caller", null));
        fieldNames.setCallerClass(map.getOrDefault("callerClass", "caller_class_name"));
        fieldNames.setCallerMethod(map.getOrDefault("callerMethod", "caller_method_name"));
        fieldNames.setCallerFile(map.getOrDefault("callerFile", "caller_file_name"));
        fieldNames.setCallerLine(map.getOrDefault("callerLine", "caller_line_number"));
        fieldNames.setStackTrace(map.getOrDefault("stackTrace", "stack_trace"));
        fieldNames.setTags(map.getOrDefault("tags", "tags"));
        fieldNames.setMdc(map.getOrDefault("mdc", null));
        fieldNames.setContext(map.getOrDefault("context", null));

        return fieldNames;
    }
}
