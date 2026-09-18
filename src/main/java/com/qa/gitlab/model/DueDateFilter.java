package com.qa.gitlab.model;

/**
 * Shortcut values of the {@code due_date} query parameter.
 * <p>
 * {@link #NO_DUE_DATE} is the odd one out - its wire value is the literal {@code "0"}, which is
 * exactly the kind of detail worth hiding behind a name.
 */
public enum DueDateFilter {

    NO_DUE_DATE("0"),
    OVERDUE("overdue"),
    WEEK("week"),
    MONTH("month"),
    NEXT_MONTH_AND_PREVIOUS_TWO_WEEKS("next_month_and_previous_two_weeks");

    private final String wireValue;

    DueDateFilter(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
