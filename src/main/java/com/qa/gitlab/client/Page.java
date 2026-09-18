package com.qa.gitlab.client;

import io.restassured.response.Response;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One page of a listing, together with the pagination metadata GitLab returns in headers.
 * <p>
 * This type exists because pagination is a contract carried almost entirely <em>outside</em> the
 * response body. A suite that only deserializes the JSON array cannot assert that {@code X-Total}
 * is right, that {@code X-Next-Page} is empty on the last page, or that the {@code Link} header
 * offers a usable {@code rel="next"} - so it silently does not test pagination at all, which is the
 * single most commonly skipped part of a list endpoint.
 *
 * @param items      the deserialized page contents
 * @param total      {@code X-Total}; absent for collections GitLab declines to count (over 10,000)
 * @param totalPages {@code X-Total-Pages}; absent for the same reason
 * @param page       {@code X-Page}, the 1-based index of this page
 * @param perPage    {@code X-Per-Page}, the page size actually applied by the server
 * @param nextPage   {@code X-Next-Page}, absent on the last page
 * @param prevPage   {@code X-Prev-Page}, absent on the first page
 * @param links      parsed {@code Link} header, keyed by {@code rel}
 */
public record Page<T>(
        List<T> items,
        Optional<Integer> total,
        Optional<Integer> totalPages,
        Optional<Integer> page,
        Optional<Integer> perPage,
        Optional<Integer> nextPage,
        Optional<Integer> prevPage,
        Map<String, String> links) {

    /** {@code <https://...>; rel="next"} - one entry per comma-separated segment. */
    private static final Pattern LINK_SEGMENT = Pattern.compile("<([^>]+)>\\s*;\\s*rel=\"([^\"]+)\"");

    public static <T> Page<T> from(Response response, Class<T[]> arrayType) {
        return of(List.of(response.as(arrayType)), response::getHeader);
    }

    /**
     * Header-parsing seam, kept separate from the HTTP call.
     * <p>
     * Two details here are easy to get wrong and both fail quietly: GitLab sends
     * {@code X-Next-Page} as an <em>empty string</em> on the last page rather than omitting it, and
     * the {@code Link} header packs several URLs into one comma-separated value whose commas also
     * appear inside the URLs' own query strings.
     *
     * @param headers header lookup by name, returning {@code null} when absent
     */
    public static <T> Page<T> of(List<T> items, java.util.function.UnaryOperator<String> headers) {
        return new Page<>(
                items,
                intHeader(headers, "X-Total"),
                intHeader(headers, "X-Total-Pages"),
                intHeader(headers, "X-Page"),
                intHeader(headers, "X-Per-Page"),
                intHeader(headers, "X-Next-Page"),
                intHeader(headers, "X-Prev-Page"),
                parseLinks(headers.apply("Link")));
    }

    public boolean hasNext() {
        return nextPage.isPresent() || links.containsKey("next");
    }

    /**
     * GitLab sends these headers as an empty string rather than omitting them when there is no
     * value - so "present but blank" has to collapse to absent, or every caller repeats the check.
     */
    private static Optional<Integer> intHeader(java.util.function.UnaryOperator<String> headers, String name) {
        String value = headers.apply(name);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            throw new AssertionError("Header " + name + " should be numeric but was '" + value + "'", e);
        }
    }

    private static Map<String, String> parseLinks(String header) {
        if (header == null || header.isBlank()) {
            return Map.of();
        }
        Matcher matcher = LINK_SEGMENT.matcher(header);
        Map<String, String> links = new java.util.LinkedHashMap<>();
        while (matcher.find()) {
            links.put(matcher.group(2), matcher.group(1));
        }
        return Map.copyOf(links);
    }
}
