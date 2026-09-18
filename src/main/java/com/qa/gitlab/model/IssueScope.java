package com.qa.gitlab.model;

/** Values of the {@code scope} query parameter: whose issues to return. */
public enum IssueScope {

    CREATED_BY_ME,
    ASSIGNED_TO_ME,
    ALL;

    public String wireValue() {
        return name().toLowerCase();
    }
}
