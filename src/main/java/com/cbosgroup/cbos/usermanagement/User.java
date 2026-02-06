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
public class User {

    private UUID userId;

    private String username;

    private String email;

    private String passwordHash;

    private String firstName;

    private String lastName;

    private String displayName;

    private String phone;

    private String avatarUrl;

    @Builder.Default
    private Boolean isActive = true;

    @Builder.Default
    private Boolean isVerified = false;

    @Builder.Default
    private Boolean isLocked = false;

    @Builder.Default
    private Integer failedLoginCount = 0;

    private Instant createdAt;

    private Instant updatedAt;

    private Instant lastLoginAt;

    private Instant lockedUntil;

    private Instant deletedAt;

    @Builder.Default
    private Set<Role> roles = new HashSet<>();
}
