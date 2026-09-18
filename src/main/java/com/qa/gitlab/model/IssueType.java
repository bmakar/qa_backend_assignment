package com.qa.gitlab.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Values of {@code issue_type}.
 * <p>
 * {@code test_case} appears in some GitLab documentation but is not creatable through this endpoint
 * on the Free tier - the API accepts it and silently returns an {@link #ISSUE}. It is therefore
 * omitted here and covered as a documented quirk in the boundary suite instead.
 */
public enum IssueType {

    ISSUE,
    INCIDENT,
    TASK;

    @JsonValue
    public String wireValue() {
        return name().toLowerCase();
    }
}
