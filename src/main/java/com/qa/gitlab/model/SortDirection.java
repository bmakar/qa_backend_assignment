package com.qa.gitlab.model;

/** Values of the {@code sort} query parameter. GitLab defaults to {@link #DESC}. */
public enum SortDirection {

    ASC,
    DESC;

    public String wireValue() {
        return name().toLowerCase();
    }
}
