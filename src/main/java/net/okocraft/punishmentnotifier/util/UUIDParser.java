package net.okocraft.punishmentnotifier.util;

import java.util.UUID;

public final class UUIDParser {

    public static UUID parse(String str) {
        if (str == null || str.isEmpty()) {
            return null;
        }

        try {
            return UUID.fromString(str);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private UUIDParser() {
        throw new UnsupportedOperationException();
    }
}
