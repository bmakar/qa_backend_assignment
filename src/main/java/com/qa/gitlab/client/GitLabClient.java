package com.qa.gitlab.client;

import com.qa.gitlab.config.Config;
import com.qa.gitlab.support.Json;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.LogConfig;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.filter.log.LogDetail;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import java.util.Set;

/**
 * Transport layer: base URI, authentication and JSON mapping in one place.
 * <p>
 * REST Assured is used for the HTTP call, for (de)serialization, and - in the typed methods on
 * {@link IssuesApi} - for asserting the documented success status. Response content is asserted in
 * the tests with AssertJ against typed models; the fluent
 * {@code .then().body("title", equalTo(...))} style is avoided because it has no compile-time
 * safety and produces poor failure messages.
 * <p>
 * Note the consequence of using {@code .then().statusCode(...)}: an unexpected status throws
 * {@link AssertionError}, so Surefire reports it as a <em>failure</em> rather than an <em>error</em>
 * even when it happened in a fixture. {@link RestAssured#enableLoggingOfRequestAndResponseIfValidationFails()}
 * below is what makes those diagnosable, by dumping the request and response when a status check
 * fails.
 * <p>
 * This class carries no dependency on a reporting library. A concern that belongs to the test run
 * rather than to the client - Allure attachments, for instance - is added by chaining
 * {@code .filter(...)} onto the {@link RequestSpecification} this returns, at each call site in the
 * test layer that needs it (see {@code ApiTest}). That is three call sites to touch instead of one
 * central registration point - deliberately, in exchange for this class having nothing to explain
 * beyond "here is a request spec".
 */
public final class GitLabClient {

    /** Header GitLab uses for personal access tokens. */
    private static final String TOKEN_HEADER = "PRIVATE-TOKEN";

    /** Stateless, so one instance is shared by every specification. */
    private static final RetryFilter RETRY = new RetryFilter();

    /**
     * {@code -Dgitlab.verboseLogging=true} (wired to the {@code verbose} Maven profile) dumps every
     * request and response, pass or fail - useful when chasing something that isn't itself a test
     * failure, e.g. confirming what a fixture actually sent. Off by default so a normal run stays
     * quiet.
     * <p>
     * <b>Does not</b> read {@link #restAssuredConfig()}'s header blacklist - confirmed the hard way,
     * by running a live request through it and finding the raw token in the console output.
     * {@code RequestLoggingFilter}'s and {@code ResponseLoggingFilter}'s no-arg constructors default
     * {@code blacklistedHeaders} to an empty set; that config-driven blacklist only applies to
     * {@link RestAssured#enableLoggingOfRequestAndResponseIfValidationFails()} below, a separate code
     * path. The token has to be blacklisted again, explicitly, when constructing
     * {@code RequestLoggingFilter} in {@link #base()}.
     */
    private static final boolean VERBOSE = Boolean.getBoolean("gitlab.verboseLogging");

    static {
        // Only log when something went wrong - a passing run stays quiet, a failing one is debuggable.
        // This fires on ANY unexpected status, not only the tests that deliberately try one - a
        // genuine 500 from GitLab dumps the request too, which is exactly why the header blacklist
        // in restAssuredConfig() has to cover this path and not only the Allure attachment.
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    private GitLabClient() {
    }

    /** Authenticated request specification for the configured token. */
    public static RequestSpecification spec() {
        return withToken(Config.token());
    }

    /** Request specification carrying an arbitrary token - used to cover 401 responses. */
    public static RequestSpecification withToken(String token) {
        return RestAssured.given().spec(base().addHeader(TOKEN_HEADER, token).build());
    }

    /** Unauthenticated request specification - used to cover anonymous access. */
    public static RequestSpecification anonymous() {
        return RestAssured.given().spec(base().build());
    }

    private static RequestSpecBuilder base() {
        RequestSpecBuilder builder = new RequestSpecBuilder()
                .setBaseUri(Config.baseUri())
                .setBasePath(Config.basePath())
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .addFilter(RETRY)
                .setConfig(restAssuredConfig());
        if (VERBOSE) {
            // ResponseLoggingFilter has no blacklistedHeaders overload at all - not needed here since
            // GitLab never echoes PRIVATE-TOKEN back in a response header, confirmed against a live
            // response. If a future header carries a secret both ways, this would need revisiting.
            builder.addFilter(new RequestLoggingFilter(LogDetail.ALL, true, System.out, true, Set.of(TOKEN_HEADER)))
                    .addFilter(new ResponseLoggingFilter());
        }
        return builder;
    }

    private static RestAssuredConfig restAssuredConfig() {
        // Must start from RestAssured.config(), not a fresh RestAssuredConfig.config(): the request
        // spec's setConfig() below replaces the effective config wholesale rather than merging into
        // it, so building from scratch here silently dropped
        // enableLoggingOfRequestAndResponseIfValidationFails() from the static initializer above -
        // confirmed directly: a deliberately failing .then().statusCode(...) produced no console
        // dump at all until this was changed to build on top of the global config instead.
        return RestAssured.config()
                // Hand REST Assured the framework's single mapper so snake_case applies both ways.
                .objectMapperConfig(
                        new ObjectMapperConfig().jackson2ObjectMapperFactory((type, charset) -> Json.MAPPER))
                // Masks the token in the console dump enableLoggingOfRequestAndResponseIfValidationFails
                // produces above. Without this, any unexpected status - not just a deliberate
                // malformed-token test - prints the live PAT to stdout, and from there into whatever
                // captures it: a terminal scrollback, a pasted failure, or Surefire's own
                // <system-out> capture in the JUnit XML that CI uploads as an artifact on every run.
                .logConfig(RestAssured.config().getLogConfig().blacklistHeader(TOKEN_HEADER));
    }
}
