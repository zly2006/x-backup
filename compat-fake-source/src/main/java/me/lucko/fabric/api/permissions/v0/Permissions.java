package me.lucko.fabric.api.permissions.v0;

import net.minecraft.command.CommandSource;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public interface Permissions {
    static boolean check(@NotNull CommandSource source, @NotNull String permission, boolean defaultValue) {
        throw new AssertionError("Stub!");
    }

    static boolean check(@NotNull CommandSource source, @NotNull String permission, int defaultRequiredLevel) {
        throw new AssertionError("Stub!");
    }

    static boolean check(@NotNull CommandSource source, @NotNull String permission) {
        throw new AssertionError("Stub!");
    }
}
