package com.qa.gitlab.testkit;

import com.qa.gitlab.support.Json;
import io.qameta.allure.attachment.AttachmentData;
import io.qameta.allure.attachment.AttachmentProcessor;
import io.qameta.allure.attachment.DefaultAttachmentProcessor;
import io.qameta.allure.attachment.FreemarkerAttachmentRenderer;
import io.qameta.allure.attachment.http.HttpRequestAttachment;
import io.qameta.allure.attachment.http.HttpResponseAttachment;
import io.restassured.filter.FilterContext;
import io.restassured.filter.OrderedFilter;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Attaches the request and response of every call to the Allure report, with credentials masked.
 * <p>
 * This is the single highest-value thing Allure does for an API suite, and it needs no annotations
 * on any test: when an assertion fails in CI, the report shows the exact URL, query string and JSON
 * that crossed the wire, instead of only {@code expected: [42] but was: []}.
 *
 * <h2>Why not allure-rest-assured</h2>
 * The obvious choice would be {@code AllureRestAssured}, and this class deliberately replaces it.
 * That filter copies headers straight from the request specification into the attachment with no
 * redaction hook - so the {@code PRIVATE-TOKEN} personal access token is written into
 * {@code allure-results}, and from there into whatever artifact or GitHub Pages site the report is
 * published to.
 * <p>
 * REST Assured's own {@code blacklistHeader} does not help <em>here</em> - it governs REST
 * Assured's own console logging, which is a different leak path and is masked separately in
 * {@code GitLabClient.restAssuredConfig()}. It has no effect on Allure's attachment builder, which
 * is why this class exists rather than relying on it for both.
 * <p>
 * So the attachment is built here instead, against the same {@link HttpRequestAttachment} model and
 * the same {@code http-request.ftl} / {@code http-response.ftl} templates that ship with
 * allure-attachments - identical report output, with {@link #REDACTED_HEADERS} masked on the way in.
 *
 * <h2>Ordering</h2>
 * Runs <em>outside</em> {@link RetryFilter}, so a call that was rate-limited and retried produces
 * one attachment showing its final outcome rather than one per attempt. The retries are still
 * visible - {@code RetryFilter} logs each one.
 */
public final class AllureAttachmentFilter implements OrderedFilter {

    /** Headers whose values must never reach the report. Compared lowercase. */
    private static final Set<String> REDACTED_HEADERS = Set.of(
            "private-token",
            "authorization",
            "proxy-authorization",
            "cookie",
            "set-cookie",
            "x-csrf-token");

    private static final String MASK = "***";

    private static final AttachmentProcessor<AttachmentData> PROCESSOR = new DefaultAttachmentProcessor();

    @Override
    public Response filter(FilterableRequestSpecification requestSpec,
                           FilterableResponseSpecification responseSpec,
                           FilterContext context) {

        // getURI() already carries the query string, so the filters under test are visible here.
        String uri = requestSpec.getURI();

        PROCESSOR.addAttachment(
                requestAttachment(requestSpec.getMethod(), uri,
                        redact(headersOf(requestSpec)), bodyOf(requestSpec)),
                new FreemarkerAttachmentRenderer("http-request.ftl"));

        Response response = context.next(requestSpec, responseSpec);

        PROCESSOR.addAttachment(
                responseAttachment(uri, response.statusCode(),
                        redact(headersOf(response)), bodyOf(response)),
                new FreemarkerAttachmentRenderer("http-response.ftl"));

        return response;
    }

    @Override
    public int getOrder() {
        return OrderedFilter.LOWEST_PRECEDENCE - 100;
    }
    /**
     * Builds the request attachment.
     * <p>
     * {@code setBody} is called only for a body that exists. Allure's builder does
     * {@code Objects.requireNonNull(body, "Body should not be null value")}, so passing null throws -
     * and a GET or DELETE has no body, which is most of this suite. Removing this guard breaks every
     * bodyless request with an error that names the report rather than the API.
     */
    private static HttpRequestAttachment requestAttachment(String method, String uri,
                                                          Map<String, String> headers, String body) {
        HttpRequestAttachment.Builder builder = HttpRequestAttachment.Builder
                .create("Request", uri)
                .setMethod(method)
                .setHeaders(headers);
        if (body != null) {
            builder.setBody(body);
        }
        return builder.build();
    }

    /** Builds the response attachment, with the same null-body guard as the request. */
    private static HttpResponseAttachment responseAttachment(String uri, int statusCode,
                                                            Map<String, String> headers, String body) {
        HttpResponseAttachment.Builder builder = HttpResponseAttachment.Builder
                .create("Response")
                .setUrl(uri)
                .setResponseCode(statusCode)
                .setHeaders(headers);
        if (body != null) {
            builder.setBody(body);
        }
        return builder.build();
    }

    /** A 204 or an unrouted method has no body; empty is normalised to null so it is simply omitted. */
    private static String bodyOf(Response response) {
        try {
            String body = response.getBody() == null ? null : response.getBody().asString();
            return body == null || body.isEmpty() ? null : body;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Masks the value of every sensitive header, keeping the name so the report still shows that
     * authentication was sent.
     * <p>
     * A regression here would leak a credential into the published report without failing any test -
     * the failure is silent by construction. Verify by hand after touching this: run one test and
     * check that {@code target/allure-results} contains no {@code glpat-} string.
     */
    private static Map<String, String> redact(Map<String, String> headers) {
        Map<String, String> safe = new LinkedHashMap<>();
        headers.forEach((name, value) ->
                safe.put(name, isSensitive(name) ? MASK : value));
        return safe;
    }

    private static boolean isSensitive(String name) {
        return name != null && REDACTED_HEADERS.contains(name.trim().toLowerCase(Locale.ROOT));
    }

    private static Map<String, String> headersOf(FilterableRequestSpecification requestSpec) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (Header header : requestSpec.getHeaders()) {
            headers.put(header.getName(), header.getValue());
        }
        return headers;
    }

    private static Map<String, String> headersOf(Response response) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (Header header : response.getHeaders()) {
            headers.put(header.getName(), header.getValue());
        }
        return headers;
    }

    /**
     * At filter time the body is still the object the test passed in, not serialized JSON, so it is
     * rendered through the framework's mapper. That way the attachment shows what the server will
     * actually receive - including the snake_case and comma-separated-label encoding - rather than a
     * record's {@code toString()}.
     */
    private static String bodyOf(FilterableRequestSpecification requestSpec) {
        Object body = requestSpec.getBody();
        if (body == null) {
            return null;
        }
        if (body instanceof String text) {
            return text;
        }
        if (body instanceof byte[] bytes) {
            return new String(bytes);
        }
        try {
            return Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(body);
        } catch (Exception e) {
            // An unserializable body must not fail the test - the report just shows less detail.
            return String.valueOf(body);
        }
    }

}
