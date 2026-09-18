package com.qa.gitlab.client;

import io.restassured.filter.FilterContext;
import io.restassured.filter.OrderedFilter;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Retries the responses that mean "ask again later", so a rate limit does not read as a test failure.
 * <p>
 * gitlab.com rate-limits aggressively and the suite is the kind of client that trips it. Without
 * this, a green suite goes red for reasons that have nothing to do with the API contract - the worst
 * category of flake, because it trains people to re-run rather than investigate.
 * <p>
 * The policy is deliberately asymmetric:
 * <ul>
 *   <li><b>429</b> is retried for every method. The request was rejected before it was processed, so
 *       replaying it cannot duplicate a side effect.</li>
 *   <li><b>502/503/504</b> are retried only for idempotent methods. A {@code POST} that times out
 *       may well have created the issue, and retrying it would create a second one - a silent
 *       duplicate is worse than a visible failure.</li>
 *   <li><b>500</b> is never retried. It is a genuine server error and a finding worth reporting, not
 *       a transient condition to paper over.</li>
 * </ul>
 * {@code Retry-After} is honoured when present, because guessing a backoff when the server has told
 * you the answer is how you stay rate-limited.
 */
public final class RetryFilter implements OrderedFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RetryFilter.class);

    private static final int RATE_LIMITED = 429;
    private static final Set<Integer> TRANSIENT_GATEWAY_STATUSES = Set.of(502, 503, 504);
    private static final Set<String> IDEMPOTENT_METHODS = Set.of("GET", "HEAD", "PUT", "DELETE", "OPTIONS");

    private static final int MAX_ATTEMPTS = 4;
    private static final Duration BASE_DELAY = Duration.ofMillis(500);
    private static final Duration MAX_DELAY = Duration.ofSeconds(20);

    @Override
    public Response filter(FilterableRequestSpecification requestSpec,
                           FilterableResponseSpecification responseSpec,
                           FilterContext context) {

        Response response = context.next(requestSpec, responseSpec);

        for (int attempt = 1; attempt < MAX_ATTEMPTS; attempt++) {
            if (!shouldRetry(requestSpec.getMethod(), response.statusCode())) {
                return response;
            }

            int currentAttempt = attempt;
            Duration delay = retryAfter(response).orElseGet(() -> backoff(currentAttempt));
            LOG.warn("{} {} returned {} - retrying in {} ms (attempt {}/{})",
                    requestSpec.getMethod(), requestSpec.getURI(), response.statusCode(),
                    delay.toMillis(), attempt + 1, MAX_ATTEMPTS);

            sleep(delay);
            response = context.next(requestSpec, responseSpec);
        }
        return response;
    }

    private static boolean shouldRetry(String method, int status) {
        if (status == RATE_LIMITED) {
            return true;
        }
        return TRANSIENT_GATEWAY_STATUSES.contains(status)
                && IDEMPOTENT_METHODS.contains(method.toUpperCase());
    }

    /** {@code Retry-After} in delta-seconds form, which is what GitLab sends. */
    private static java.util.Optional<Duration> retryAfter(Response response) {
        String header = response.getHeader("Retry-After");
        if (header == null || header.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(cap(Duration.ofSeconds(Long.parseLong(header.trim()))));
        } catch (NumberFormatException e) {
            // An HTTP-date form is legal but GitLab does not use it; fall back to the backoff.
            return java.util.Optional.empty();
        }
    }

    /** Exponential with full jitter - identical backoff across parallel classes would re-collide. */
    private static Duration backoff(int attempt) {
        long ceiling = BASE_DELAY.toMillis() * (1L << (attempt - 1));
        return cap(Duration.ofMillis(ThreadLocalRandom.current().nextLong(BASE_DELAY.toMillis(), ceiling + 1)));
    }

    private static Duration cap(Duration delay) {
        return delay.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : delay;
    }

    private static void sleep(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while backing off from a rate limit", e);
        }
    }

    /**
     * Runs closest to the transport so a retry re-executes the request itself rather than any
     * logging or reporting filter wrapped around it.
     */
    @Override
    public int getOrder() {
        return OrderedFilter.LOWEST_PRECEDENCE;
    }
}
