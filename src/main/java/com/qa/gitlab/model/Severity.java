package com.qa.gitlab.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * Values of {@code severity}.
 * <p>
 * Only meaningful on an issue whose {@code issue_type} is {@code incident} - the field is accepted
 * on any issue but carries no meaning elsewhere. The response returns it uppercased
 * ({@code "UNKNOWN"}) while the request takes it lowercased, which is why the wire form is spelled
 * out here rather than derived at the call site.
 */
public enum Severity {

    UNKNOWN,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    @JsonValue
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Case-insensitive, because this field is the one place the two directions disagree: the request
     * takes {@code "critical"} and the response returns {@code "CRITICAL"}. Handled here rather than
     * by relaxing enum matching on the shared mapper - a global case-insensitive setting would also
     * stop the suite noticing if GitLab ever changed the case of a value it currently sends exactly.
     */
    @JsonCreator
    public static Severity fromWire(String value) {
        return value == null ? null : valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
