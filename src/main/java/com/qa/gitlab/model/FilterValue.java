package com.qa.gitlab.model;

/**
 * A filter value that may be a concrete id, a literal, or one of GitLab's two magic words.
 * <p>
 * Several list filters ({@code assignee_id}, {@code milestone}, {@code labels}) accept either a real
 * value or the strings {@code Any} and {@code None}. A bare {@code Long} cannot express that, and a
 * bare {@code String} loses compile-time safety at the call site, so construction goes through
 * named factories: {@code FilterValue.none()} reads better than {@code "None"} and cannot be
 * misspelled.
 * <p>
 * Note the capitalisation - GitLab matches {@code Any}/{@code None} case-sensitively.
 */
public record FilterValue(String wireValue) {

    private static final FilterValue ANY = new FilterValue("Any");
    private static final FilterValue NONE = new FilterValue("None");

    /** Match a specific numeric id. */
    public static FilterValue of(long id) {
        return new FilterValue(String.valueOf(id));
    }

    /** Match a specific literal, such as a milestone title. */
    public static FilterValue of(String literal) {
        return new FilterValue(literal);
    }

    /** Match records that have any value for this field. */
    public static FilterValue any() {
        return ANY;
    }

    /** Match records that have no value for this field. */
    public static FilterValue none() {
        return NONE;
    }

    @Override
    public String toString() {
        return wireValue;
    }
}
