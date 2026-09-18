package com.qa.gitlab.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * An issue as returned by the API.
 * <p>
 * A record rather than a Lombok class: accessors, equals/hashCode and a readable toString come for
 * free, Jackson deserializes records natively, and immutability means a test cannot accidentally
 * mutate a response it is asserting on. Immutability is also what makes AssertJ's
 * {@code usingRecursiveComparison()} pleasant to work with.
 * <p>
 * Note {@link #iid} - issues are addressed in URLs by their per-project internal id, not by the
 * globally unique {@link #id}. Using the wrong one is the most common mistake against this API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Issue(

        long id,

        long iid,

        long projectId,

        String title,

        String description,

        IssueState state,

        IssueType issueType,

        List<String> labels,

        boolean confidential,

        LocalDate dueDate,

        LocalDate startDate,

        /** Returned uppercased ({@code "UNKNOWN"}); {@link Severity} maps both directions. */
        Severity severity,

        String webUrl,

        Instant createdAt,

        Instant updatedAt,

        Instant closedAt,

        Author author,

        List<Author> assignees) {

    /**
     * Longest title the API accepts, in characters. 255 is validated on create and update, so 255
     * must be accepted and 256 rejected.
     * <p>
     * A constraint on requests rather than on responses, which is why it is asserted in the boundary
     * tests and deliberately not in {@code schemas/issue.json} - see the {@code title} note there.
     * It lives here rather than in the test fixtures because it is a fact about the API, not test
     * data, and a caller using this client to seed data needs it just as much as a test does.
     */
    public static final int TITLE_MAX_LENGTH = 255;

    public boolean isOpen() {
        return state == IssueState.OPENED;
    }
}
