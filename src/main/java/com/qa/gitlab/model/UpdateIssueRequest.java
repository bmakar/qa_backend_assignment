package com.qa.gitlab.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDate;
import java.util.List;

/**
 * Payload for {@code PUT /projects/:id/issues/:iid}.
 * <p>
 * Note that closing and reopening an issue is an update carrying a {@link StateEvent}, not a
 * delete - the state transition is the operation most suites actually need to cover.
 */
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateIssueRequest(

        String title,

        String description,

        /**
         * Comma-separated, the shape the API documents. An empty string ({@code ""}, not {@code null})
         * is how the API clears every label - {@code null} omits the field and leaves labels untouched.
         */
        String labels,

        /** Comma-separated labels to add to the existing set. */
        String addLabels,

        /** Comma-separated labels to remove from the existing set. */
        String removeLabels,

        Boolean confidential,

        LocalDate dueDate,

        List<Long> assigneeIds,

        Long milestoneId,

        IssueType issueType,

        Boolean discussionLocked,

        StateEvent stateEvent) {
}
