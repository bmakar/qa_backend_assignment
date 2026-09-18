package com.qa.gitlab.model;

import com.fasterxml.jackson.annotation.JsonValue;

/** Lifecycle transition passed as {@code state_event} on an issue update. */
public enum StateEvent {

    CLOSE,
    REOPEN;

    @JsonValue
    public String wireValue() {
        return name().toLowerCase();
    }
}
