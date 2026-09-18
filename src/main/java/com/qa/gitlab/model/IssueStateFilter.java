package com.qa.gitlab.model;

/**
 * Accepted values of the {@code state} query parameter on the list endpoint.
 * <p>
 * Note {@link #ALL}, which has no equivalent in {@link IssueState} - it is a filter instruction,
 * not a state.
 */
public enum IssueStateFilter {

    OPENED,
    CLOSED,
    ALL;

    public String wireValue() {
        return name().toLowerCase();
    }
}
