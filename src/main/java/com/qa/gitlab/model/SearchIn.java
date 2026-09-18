package com.qa.gitlab.model;

/** Fields the {@code search} parameter is matched against, passed as {@code in}. */
public enum SearchIn {

    TITLE,
    DESCRIPTION;

    public String wireValue() {
        return name().toLowerCase();
    }
}
