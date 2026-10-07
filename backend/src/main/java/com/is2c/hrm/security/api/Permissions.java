package com.is2c.hrm.security.api;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Permission strings {@code MODULE:ACTION} (FR-005). */
public final class Permissions {

    public static final List<String> ALL = Arrays.stream(PermissionModule.values())
            .flatMap(m -> Arrays.stream(PermissionAction.values()).map(a -> of(m, a)))
            .toList();

    private static final Set<String> ALL_SET = Set.copyOf(ALL);

    private Permissions() {
    }

    public static String of(PermissionModule module, PermissionAction action) {
        return module.name() + ":" + action.name();
    }

    public static boolean isValid(String permission) {
        return ALL_SET.contains(permission);
    }

    public static Set<String> allAsSet() {
        return ALL.stream().collect(Collectors.toUnmodifiableSet());
    }
}
