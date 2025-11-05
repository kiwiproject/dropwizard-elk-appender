package org.kiwiproject.elk;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables a JUnit Jupiter test class or method only when the
 * {@code fullIntegrationTests} system property is defined.
 * Note that there doesn't need to be a value, and if there is
 * a value, it doesn't matter what it is.
 * <p>
 * You can run integration tests like: {@code mvn test -DfullIntegrationTests}
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@EnabledIfSystemProperty(named = "fullIntegrationTests", matches = ".*")
public @interface EnabledForFullIntegrationTests {
}
