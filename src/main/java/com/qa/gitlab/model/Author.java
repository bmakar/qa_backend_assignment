package com.qa.gitlab.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A user reference, used for issue authors and assignees. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Author(

        long id,

        String username,

        String name,

        String state,

        String webUrl) {
}
