package me.lucko.fabric.api.permissions.v0;

import net.minecraft.commands.SharedSuggestionProvider;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public interface Permissions {
    static boolean check(@NotNull SharedSuggestionProvider source, @NotNull String permission, boolean defaultValue) {
        throw new AssertionError("Stub!");
    }

    static boolean check(@NotNull SharedSuggestionProvider source, @NotNull String permission, int defaultRequiredLevel) {
        throw new AssertionError("Stub!");
    }

    static boolean check(@NotNull SharedSuggestionProvider source, @NotNull String permission) {
        throw new AssertionError("Stub!");
    }
}
