package net.minecraft.commands;

public interface CommandSource {
    boolean hasPermissionLevel(int level);
}
