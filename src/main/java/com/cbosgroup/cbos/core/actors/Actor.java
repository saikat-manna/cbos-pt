package com.cbosgroup.cbos.core.actors;

import lombok.Builder;
import lombok.Data;

/**
 * Represents an actor participating in a flow.
 */
@Data
@Builder
public class Actor {

    private String actorId;

    private String name;

    /**
     * Role of this actor (arbitrary string, e.g., "approver", "applicant")
     */
    private String role;

    private String email;
}
