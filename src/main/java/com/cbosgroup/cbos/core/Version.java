package com.cbosgroup.cbos.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a version identifier for flows, documents, etc.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Version {

    private int major;
    private int minor;
    private int patch;
    private String label;

    public static Version of(int major, int minor, int patch) {
        return Version.builder()
                .major(major)
                .minor(minor)
                .patch(patch)
                .build();
    }

    @Override
    public String toString() {
        String version = major + "." + minor + "." + patch;
        return label != null ? version + "-" + label : version;
    }
}
