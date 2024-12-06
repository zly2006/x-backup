package net.minecraft.command;

public interface CommandSource {
    boolean hasPermissionLevel(int level);
}
