package com.qa.gitlab.model;

/**
 * Values of the {@code order_by} query parameter.
 * <p>
 * Only the Free-tier fields are modelled; {@code weight} and {@code popularity} variants that
 * depend on paid features are left out so a test cannot silently assert on an ignored parameter.
 */
public enum IssueOrderBy {

    CREATED_AT,
    UPDATED_AT,
    TITLE,
    DUE_DATE,
    RELATIVE_POSITION,
    LABEL_PRIORITY;

    public String wireValue() {
        return name().toLowerCase();
    }
}
