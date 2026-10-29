package com.zendrahub.evolve.core.persist;

import com.zendrahub.evolve.core.model.WorldState;

/**
 * Upgrades older save layouts step by step to {@link WorldState#FORMAT_VERSION}.
 * When the format changes: bump FORMAT_VERSION, add a case that rewrites the
 * raw JSON from the previous version, and add a test with an old save.
 */
final class Migrations {
    private Migrations() {
    }

    static Fields upgrade(Fields root, int fromVersion) {
        int version = fromVersion;
        while (version < WorldState.FORMAT_VERSION) {
            switch (version) {
                // case 1 -> { rewrite root.raw() from version 1 to 2 }
                default -> throw new SaveFormatException("no migration from save format " + version);
            }
        }
        return root;
    }
}
