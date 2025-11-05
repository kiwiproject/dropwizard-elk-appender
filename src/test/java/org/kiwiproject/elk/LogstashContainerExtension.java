package org.kiwiproject.elk;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.kiwiproject.collect.KiwiMaps.isNotNullOrEmpty;
import static org.kiwiproject.test.constants.KiwiTestConstants.JSON_HELPER;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Durations;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.kiwiproject.base.UncheckedInterruptedException;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * Creates a Logstash container for use by integration tests. The container is started before
 * all tests and is stopped after all tests have completed.
 * <p>
 * This extension also sets the system properties required by {@link org.kiwiproject.config.provider.ElkLoggerConfigProvider}
 * before all tests run, and clears them after all tests complete.
 */
@Slf4j
public class LogstashContainerExtension implements BeforeAllCallback, AfterAllCallback {

    private static final DockerImageName LOGSTASH_IMAGE = 
            DockerImageName.parse("docker.elastic.co/logstash/logstash:9.2.0");

    private static final DockerImageName NETCAT_IMAGE_NAME =
            DockerImageName.parse("toolbelt/netcat:2025-10-23");

    /**
     * The type of Logstash container.
     */
    public enum LogstashContainerType {
        
        /**
         * Uses a real Logstash container.
         */
        REAL,
        
        /**
         * Simulates Logstash using netcat.
         */
        SIMULATED
    }

    @Getter
    @Accessors(fluent = true)
    private GenericContainer<?> container;

    private final LogstashContainerType containerType;

    private final Map<String, String> customFields;

    /**
     * Create a container that uses a real Logtash container.
     */
    public LogstashContainerExtension() {
        this(LogstashContainerType.REAL, Map.of());
    }

    /**
     * Create a container that uses the given container type to create a real
     * or simulated Logstash container.
     */
    @Builder
    public LogstashContainerExtension(LogstashContainerType containerType, Map<String, String> customFields) {
        this.containerType = isNull(containerType) ? LogstashContainerType.REAL : containerType;
        this.customFields = isNull(customFields) ? Map.of() : customFields;
    }

    @Override
    public void beforeAll(@NonNull ExtensionContext context) {
        container = switch (containerType) {
            case REAL -> newLogstashContainer();
            case SIMULATED -> newSimulatedLogstashContainer();
        };

        LOG.info("Starting Logstash");
        container.start();

         // Set properties for ElkLoggerConfigProvider
        var host = container.getHost();
        var port = container.getMappedPort(5044).toString();
        LOG.info("Container host {} started with port 5044 mapped to external port {}", host, port);
        
        System.setProperty("kiwi.elk.host", host);
        System.setProperty("kiwi.elk.port", port);    
        if (isNotNullOrEmpty(customFields)) {
            System.setProperty("kiwi.elk.customFields", JSON_HELPER.toJson(customFields));        
        }
    }

    @SuppressWarnings("resource")
    private static GenericContainer<?> newLogstashContainer() {
        return new GenericContainer<>(LOGSTASH_IMAGE)
                .withClasspathResourceMapping(
                    "logstash.conf", "/usr/share/logstash/pipeline/logstash.conf", BindMode.READ_ONLY)
                .withExposedPorts(5044)
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofSeconds(60));
    }

    @SuppressWarnings("resource")
    private static GenericContainer<?> newSimulatedLogstashContainer() {
        return new GenericContainer<>(NETCAT_IMAGE_NAME)
                .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("/bin/sh"))
                .withCommand("-c", "nc -l -k -p 5044 > /tmp/logs.txt")
                .withExposedPorts(5044)
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofSeconds(30));
    }

    @Override
    public void afterAll(@NonNull ExtensionContext context) {
        System.clearProperty("kiwi.elk.host");
        System.clearProperty("kiwi.elk.port");
        System.clearProperty("kiwi.elk.customFields");

        if (nonNull(container)) {
            LOG.info("Stopping Logstash");
            container.stop();
        }
    }

    /**
     * Get the container logs when the container is a real Logstash container, or the
     * netcat logs when the container is a simulated Logstash container.
     */
    public String logs() {
        return switch (containerType) {
            case REAL -> container.getLogs();
            case SIMULATED -> execInContainerReturningStdOut("cat", "/tmp/logs.txt");
        };
    }

     /**
     * Waits up to the 10 seconds for the given substring to appear in the Logstash logs.
     */
    public void awaitLogContains(String... substring) {
        awaitLogContains(Durations.TEN_SECONDS, substring);
    }

    /**
     * Waits up to the provided duration for the given substring to appear in the logs.
     */
    public void awaitLogContains(Duration timeout, String... substring) {
        await().atMost(timeout)
                .untilAsserted(() -> assertThat(logs()).contains(substring));
    }

    /**
     * Finds the first log line that contains the given substring and converts it to a Map.
     * Throws AssertionError if no matching line is found.
     */
    public Map<String, Object> findFirstLogAsMapContaining(String substring) {
        return logs().lines()
                .filter(line -> line.contains(substring))
                .map(JSON_HELPER::toMap)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No log line containing: " + substring));
    }

    /**
     * Convenience method to execute a command in the container and return its standard output.
     */
    public String execInContainerReturningStdOut(String... command) {
        return execInContainer(command).getStdout();
    }

    /**
     * Convenience method to execute a command in the container and return its result.
     * 
     * @implNote wraps the checked exceptions thrown by {@link Container#execInContainer(String...)}
     * in a runtime exception.
     */
    public Container.ExecResult execInContainer(String... command) {
        try {
            return container.execInContainer(command);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedInterruptedException(e);
        } catch (UnsupportedOperationException | IOException e) {
           throw new RuntimeException(e);
        }
    }
}
