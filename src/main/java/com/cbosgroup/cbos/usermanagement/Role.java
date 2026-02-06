package com.cbosgroup.cbos.usermanagement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    private UUID roleId;

    private String roleName;

    private String description;

    private Instant createdAt;

    @Builder.Default
    private Set<Permission> permissions = new HashSet<>();
}
