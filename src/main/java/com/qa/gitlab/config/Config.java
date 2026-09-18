package com.qa.gitlab.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

/**
 * Client configuration, resolved in this order for every value:
 * <ol>
 *   <li>system property, e.g. {@code -Dgitlab.token=...}</li>
 *   <li>environment variable, e.g. {@code GITLAB_TOKEN}</li>
 *   <li>{@code gitlab.properties} on the classpath - git-ignored, per-developer, created by
 *       copying {@code sample.gitlab.properties}; normally absent, including in CI, where the
 *       two sources above are used instead</li>
 *   <li>{@code config.properties} on the classpath - committed, non-secret defaults only</li>
 * </ol>
 * A hardcoded fallback exists only for {@link #baseUri()} and {@link #basePath()}, and only as
 * insurance against {@code config.properties} ever being missing from the classpath (a
 * resource-stripped build, for instance) - the committed file is the real source of truth, not
 * this class. A token or a real project id must never live in a committed file, which is exactly
 * why neither has an entry in {@code config.properties} or a fallback here: an absent token fails
 * loudly in {@link #token()}, and an absent project id means "create a scratch project", not
 * "point every clone of this repo at the same one".
 */
public final class Config {

    private static final Logger LOG = LoggerFactory.getLogger(Config.class);

    /** Last-resort fallback only - see the class javadoc. The real default lives in config.properties. */
    private static final String FALLBACK_BASE_URI = "https://gitlab.com";
    private static final String FALLBACK_BASE_PATH = "/api/v4";

    private static final Properties DEFAULTS = load("/config.properties", true);
    private static final Properties LOCAL = load("/gitlab.properties", false);

    private Config() {
    }

    public static String baseUri() {
        return resolve("gitlab.baseUri", "GITLAB_BASE_URI").orElse(FALLBACK_BASE_URI);
    }

    public static String basePath() {
        return resolve("gitlab.basePath", "GITLAB_BASE_PATH").orElse(FALLBACK_BASE_PATH);
    }

    public static String token() {
        return resolve("gitlab.token", "GITLAB_TOKEN").orElseThrow(() -> new IllegalStateException("""
                No GitLab token configured.

                Create a personal access token with the 'api' scope at
                  https://gitlab.com/-/user_settings/personal_access_tokens
                then any of:
                  export GITLAB_TOKEN=glpat-xxxxxxxxxxxx
                  mvn test -Dgitlab.token=glpat-xxxxxxxxxxxx
                  copy src/main/resources/sample.gitlab.properties to gitlab.properties in the
                  same directory and fill it in (git-ignored, for local development only)
                """));
    }

    /** Project the tests operate on, or empty to create a scratch project. */
    public static Optional<Long> projectId() {
        return resolve("gitlab.projectId", "GITLAB_PROJECT_ID").map(Long::parseLong);
    }

    private static Optional<String> resolve(String key, String environmentVariable) {
        return firstNonBlank(
                System.getProperty(key),
                System.getenv(environmentVariable),
                LOCAL.getProperty(key),
                DEFAULTS.getProperty(key));
    }

    private static Optional<String> firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.trim().isEmpty()) {
                return Optional.of(candidate.trim());
            }
        }
        return Optional.empty();
    }

    /**
     * @param required {@code true} for {@code config.properties}, which is packaged alongside this
     *                 class and must always be resolvable; {@code false} for
     *                 {@code gitlab.properties}, which is git-ignored and normal to be absent.
     */
    private static Properties load(String classpathResource, boolean required) {
        Properties properties = new Properties();
        try (InputStream in = Config.class.getResourceAsStream(classpathResource)) {
            if (in == null) {
                if (required) {
                    throw new IllegalStateException(
                            classpathResource + " is missing from the classpath - the build is broken");
                }
                LOG.info("{} not found on the classpath - expected unless you created one for "
                        + "local development", classpathResource);
                return properties;
            }
            properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + classpathResource, e);
        }
        return properties;
    }
}
