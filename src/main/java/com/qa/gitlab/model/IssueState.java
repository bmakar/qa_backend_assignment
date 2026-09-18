package com.qa.gitlab.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The state an issue can actually be in, as returned in the {@code state} field.
 * <p>
 * Deliberately distinct from {@link IssueStateFilter}: the filter accepts {@code all}, which is not
 * a state an issue can hold. Modelling them as one type invites tests that assert an issue is in
 * state "all".
 */
public enum IssueState {

    OPENED,
    CLOSED;

    @JsonValue
    public String wireValue() {
        return name().toLowerCase();
    }

    /** Lenient so an unexpected state fails an assertion with a readable message, not a mapping error. */
    @JsonCreator
    public static IssueState fromWire(String value) {
        for (IssueState state : values()) {
            if (state.wireValue().equalsIgnoreCase(value)) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unknown issue state returned by the API: " + value);
    }
}
