package org.kiwiproject.elk;

import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.kiwiproject.base.UncheckedInterruptedException;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;

/**
 * Creates a Logstash container for use by integration tests. The container is started before
 * all tests and is stopped after all tests have completed.
 * <p>
 * This extension also sets the system properties required by {@link org.kiwiproject.config.provider.ElkLoggerConfigProvider}
 * before all tests run, and clears them after all tests complete.
 */
@Slf4j
public class LogstashContainerExtension implements BeforeAllCallback, AfterAllCallback {

    private static final DockerImageName LOGSTASH_IMAGE = DockerImageName
            .parse("docker.elastic.co/logstash/logstash:9.2.0");

    private static final DockerImageName ALPINE_IMAGE_NAME = DockerImageName.parse("alpine:latest");

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

    /**
     * Create a container that uses a real Logtash container.
     */
    public LogstashContainerExtension() {
        this(LogstashContainerType.REAL);
    }

    /**
     * Create a container that uses the given container type to create a real
     * or simulated Logstash container.
     */
    public LogstashContainerExtension(LogstashContainerType containerType) {
        this.containerType = containerType;
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
        LOG.info("Container host {} started with port {} mapped to container 5044", host, port);
        
        System.setProperty("kiwi.elk.host", host);
        System.setProperty("kiwi.elk.port", port);
    }

    @SuppressWarnings("resource")
    private static GenericContainer<?> newLogstashContainer() {
        return new GenericContainer<>(LOGSTASH_IMAGE)
                .withExposedPorts(5044)
                .withEnv("config.string", """
                        input {
                            tcp {
                                port => 5044
                                codec => json_lines
                            }
                        }
                        output {
                            stdout { codec => json_lines }
                        }
                        """)
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofSeconds(60));
    }

    @SuppressWarnings("resource")
    private static GenericContainer<?> newSimulatedLogstashContainer() {
        return new GenericContainer<>(ALPINE_IMAGE_NAME)
                .withCommand("sh", "-c", "apk add -q --no-cache netcat-openbsd && nc -lkp 5044 > /tmp/logs.txt")
                .withExposedPorts(5044)
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofSeconds(30));
    }

    @Override
    public void afterAll(@NonNull ExtensionContext context) {
        System.clearProperty("kiwi.elk.host");
        System.clearProperty("kiwi.elk.port");

        LOG.warn("Stopping Logstash");
        container.stop();
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
