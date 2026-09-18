package com.qa.gitlab.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Payload for {@code POST /projects/:id/issues}.
 * <p>
 * A builder is worth the Lombok dependency here: only {@code title} is required and the remaining
 * fields are optional, so a hand-written builder would be pure noise and a {@code Map<String,Object>}
 * would throw away compile-time safety.
 * <p>
 * {@code NON_NULL} matters - unset optional fields must be absent from the body, not sent as
 * {@code null}, which the API would reject.
 */
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateIssueRequest(

        String title,

        String description,

        /**
         * A comma-separated string, the shape the API documents - not a {@code List<String>}. Callers
         * building more than one label join them themselves, e.g. {@code String.join(",", a, b)}.
         * An empty string is how the API is documented to clear all labels on an update; there is no
         * equivalent shorthand for create, where labels are absent by default.
         */
        String labels,

        Boolean confidential,

        List<Long> assigneeIds,

        Long milestoneId,

        /**
         * Milestone <em>title</em>, an exact case-sensitive match against a project or ancestor-group
         * milestone. Mutually exclusive with {@link #milestoneId}, which takes the global id instead -
         * sending both is a request the API rejects, so pick one.
         */
        String milestone,

        LocalDate dueDate,

        /** Start date, {@code YYYY-MM-DD}. Added to this endpoint in GitLab 19.1. */
        LocalDate startDate,

        IssueType issueType,

        /**
         * Only meaningful when {@code issueType} is {@link IssueType#INCIDENT}; accepted but inert on
         * an ordinary issue.
         */
        Severity severity,

        /**
         * Only honoured for users with administrator or project-owner rights; otherwise the API
         * accepts the request and stamps the current time instead, with nothing in the response
         * indicating the value was dropped. Covered by {@code IssueElevatedFieldTest}.
         */
        Instant createdAt,

        /**
         * Requesting a specific internal id also requires administrator or project-owner rights.
         * Covered by {@code IssueElevatedFieldTest}, including the case that must never succeed
         * quietly: reusing an iid that already exists.
         */
        Long iid,

        /**
         * Premium/Ultimate only - accepted but ignored on Free tier, where the response returns
         * {@code null}. Covered by {@code IssueElevatedFieldTest}.
         */
        Integer weight) {
}
