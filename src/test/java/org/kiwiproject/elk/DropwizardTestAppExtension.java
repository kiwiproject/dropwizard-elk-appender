package org.kiwiproject.elk;

import com.codahale.metrics.health.HealthCheck;
import io.dropwizard.core.Application;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.kiwiproject.test.logback.LogbackTestHelpers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts a small Dropwizard application before all tests run, and stops
 * it after all tests have completed.
 * <p>
 * Because {@link DropwizardAppExtension} behaves badly and completely
 * wipes out all Logback logging, this extension restores Logback
 * using {@link LogbackTestHelpers#resetLogback()}.
 */
@Slf4j
public class DropwizardTestAppExtension implements BeforeAllCallback, AfterAllCallback {

    // NOTE:
    // To ensure extension order is respected, we're starting and stopping the app
    // like a "normal" extension would by using the lifecycle callback, instead of using
    // DropwizardExtensionsSupport that does some reflective shenanigans to find fields.
    // For example, we want to ensure that Logstash starts before a Dropwizard application
    // that needs to send it log data.

    private DropwizardAppExtension<TestAppConfiguration> app;

    @Override
    public void beforeAll(@NonNull ExtensionContext context) throws Exception {
        app = new DropwizardAppExtension<>(
                TestApp.class,
                ResourceHelpers.resourceFilePath("elk-integration-test-config.yml"));

        LOG.info("Starting Dropwizard app");
        app.beforeAll(context);
    }

    @Override
    public void afterAll(@NonNull ExtensionContext context) {
        LOG.info("Stopping Dropwizard app");
        app.afterAll(context);

        LogbackTestHelpers.resetLogback();
        LOG.info("Stopped Dropwizard app, and reset Logback configuration");
    }

    /**
     * Get the {@code "integration-test"} logger, which is defined in the YAML configuration.
     */
    public Logger getIntegrationTestLogger() {
        return LoggerFactory.getLogger("integration-test");
    }

    /**
     * Simple Dropwizard app with a single "ping" endpoint and an always-healthy health check.
     */
    @Slf4j
    public static class TestApp extends Application<TestAppConfiguration> {

        @Override
        public void run(TestAppConfiguration configuration, Environment environment) {
            environment.jersey().register(new Object() {
                @GET
                @Path("/ping")
                public String ping() {
                    return "pong";
                }
            });
            environment.healthChecks().register("noop", new HealthCheck() {
                @Override
                protected Result check() {
                    return Result.healthy();
                }
            });
        }
    }

    /**
     * Simple Dropwizard app configuration class.
     */
    public static class TestAppConfiguration extends Configuration {
    }
}
