# MC 26.1.2 Naming Port Reference

> **Context**: Minecraft 26.1.2 ships unobfuscated with Mojang official class names.  
> Yarn mappings **do not exist** for this version (`net.fabricmc:yarn:26.1.2` → empty `[]`).  
> The intermediary is `0.0.0` (placeholder). Fabric Loom performs **no remapping**.  
> All source code must use Mojang official package/class names directly.

---

## 1. Core Class Mapping Table

| Old Name (Yarn / 1.21.x) | New Name (Mojang Official / 26.1.2) | Notes |
|---|---|---|
| `net.minecraft.text.Text` | `net.minecraft.network.chat.Component` | Factory methods changed (see §3) |
| `net.minecraft.text.MutableText` | `net.minecraft.network.chat.MutableComponent` | |
| `net.minecraft.text.ClickEvent` | `net.minecraft.network.chat.ClickEvent` | Same package prefix now `network.chat` |
| `net.minecraft.text.HoverEvent` | `net.minecraft.network.chat.HoverEvent` | Same |
| `net.minecraft.text.Style` | `net.minecraft.network.chat.Style` | Same |
| `net.minecraft.util.Formatting` | `net.minecraft.ChatFormatting` | Moved to root package |
| `net.minecraft.util.WorldSavePath` | `net.minecraft.world.level.storage.LevelResource` | |
| `net.minecraft.util.Identifier` | `net.minecraft.resources.ResourceLocation` | **Confirmed: no `ResourceLocation` in jar** – use `net.minecraft.resources.ResourceLocation` |
| `net.minecraft.util.Util` | **Does not exist** in jar root | `Util.getOperatingSystem()` → use `System.getProperty("os.name")` or platform detection |
| `net.minecraft.util.Util.OperatingSystem` | **Removed** | See §4 |
| `net.minecraft.server.command.ServerCommandSource` | `net.minecraft.commands.CommandSourceStack` | |
| `net.minecraft.server.MinecraftServer` | `net.minecraft.server.MinecraftServer` | **Same class name**, same package ✓ |
| `net.minecraft.server.world.ServerWorld` | `net.minecraft.server.level.ServerLevel` | |
| `net.minecraft.server.dedicated.DedicatedServerWatchdog` | `net.minecraft.server.dedicated.ServerWatchdog` | Class renamed |
| `net.minecraft.world.dimension.DimensionType` | `net.minecraft.world.level.dimension.DimensionType` | Package changed |
| `net.minecraft.world.storage.StorageIoWorker` | `net.minecraft.world.level.chunk.storage.RegionFileStorage` (approx) | Functionality moved; verify |
| `net.minecraft.screen.ScreenTexts` | `net.minecraft.network.chat.CommonComponents` | |
| `net.minecraft.client.MinecraftClient` | `net.minecraft.client.Minecraft` | Class renamed |
| `net.minecraft.client.gui.DrawContext` | `net.minecraft.client.gui.GuiGraphics` | Class renamed |
| `net.minecraft.client.gui.screen.Screen` | `net.minecraft.client.gui.screens.Screen` | Package changed (`screen` → `screens`) |
| `net.minecraft.client.gui.screen.world.SelectWorldScreen` | `net.minecraft.client.gui.screens.worldselection.SelectWorldScreen` | Package changed |
| `net.minecraft.client.gui.screen.world.WorldListWidget` | `net.minecraft.client.gui.screens.worldselection.WorldSelectionList` | Class renamed |
| `net.minecraft.client.gui.widget.ButtonWidget` | `net.minecraft.client.gui.components.Button` | Package changed (`widget` → `components`) |
| `net.minecraft.client.texture.NativeImage` | `com.mojang.blaze3d.platform.NativeImage` | Moved to blaze3d! |
| `net.minecraft.client.texture.NativeImageBackedTexture` | `net.minecraft.client.renderer.texture.DynamicTexture` | Class renamed |
| `net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket` | `net.minecraft.network.protocol.game.ClientboundTabListPacket` | Class renamed |
| `net.minecraft.command.argument.ColumnPosArgumentType` | `net.minecraft.commands.arguments.coordinates.ColumnPosArgument` | Package + class renamed |
| `net.minecraft.server.PlayerManager` | `net.minecraft.server.players.PlayerList` | Package changed |

---

## 2. Package-Level Changes Summary

| Old Package (Yarn) | New Package (Mojang) |
|---|---|
| `net.minecraft.text.*` | `net.minecraft.network.chat.*` |
| `net.minecraft.util.Formatting` | `net.minecraft` (root) → `net.minecraft.ChatFormatting` |
| `net.minecraft.util.WorldSavePath` | `net.minecraft.world.level.storage.LevelResource` |
| `net.minecraft.util.Identifier` | `net.minecraft.resources.ResourceLocation` |
| `net.minecraft.server.command.*` | `net.minecraft.commands.*` |
| `net.minecraft.server.world.*` | `net.minecraft.server.level.*` |
| `net.minecraft.server.dedicated.DedicatedServerWatchdog` | `net.minecraft.server.dedicated.ServerWatchdog` |
| `net.minecraft.world.dimension.*` | `net.minecraft.world.level.dimension.*` |
| `net.minecraft.world.storage.StorageIoWorker` | `net.minecraft.world.level.chunk.storage.RegionFileStorage` |
| `net.minecraft.screen.ScreenTexts` | `net.minecraft.network.chat.CommonComponents` |
| `net.minecraft.client.MinecraftClient` | `net.minecraft.client.Minecraft` |
| `net.minecraft.client.gui.DrawContext` | `net.minecraft.client.gui.GuiGraphics` |
| `net.minecraft.client.gui.screen.*` | `net.minecraft.client.gui.screens.*` |
| `net.minecraft.client.gui.screen.world.*` | `net.minecraft.client.gui.screens.worldselection.*` |
| `net.minecraft.client.gui.widget.*` | `net.minecraft.client.gui.components.*` |
| `net.minecraft.client.texture.*` | `net.minecraft.client.renderer.texture.*` + `com.mojang.blaze3d.platform.*` |
| `net.minecraft.network.packet.s2c.play.*` | `net.minecraft.network.protocol.game.Clientbound*` |
| `net.minecraft.command.argument.*` | `net.minecraft.commands.arguments.*` |

---

## 3. API / Method Changes

### Text / Component

| Old (1.21.x Yarn) | New (26.1.2 Official) |
|---|---|
| `Text.literal(str)` | `Component.literal(str)` |
| `Text.translatable(key)` | `Component.translatable(key)` |
| `Text.translatable(key, args...)` | `Component.translatable(key, args...)` |
| `Text.translatableWithFallback(key, fallback, args...)` | `Component.translatableWithFallback(key, fallback, args...)` *(check if exists)* |
| `Text.empty()` | `Component.empty()` |
| `Text.of(str)` | `Component.literal(str)` |
| `text.formatted(Formatting.RED)` | `component.withStyle(ChatFormatting.RED)` |
| `text.styled { it.withColor(...) }` | `component.withStyle { it.withColor(...) }` |
| `text.withHoverEvent(HoverEvent(...))` | `component.withStyle(Style.EMPTY.withHoverEvent(...))` |
| `text.withClickEvent(ClickEvent(...))` | `component.withStyle(Style.EMPTY.withClickEvent(...))` |
| `mutableText.append(other)` | `mutableComponent.append(other)` ✓ same |
| `mutableText.copy()` | `mutableComponent.copy()` ✓ same |

### ClickEvent

| Old (Yarn) | New (Official) |
|---|---|
| `ClickEvent(ClickEvent.Action.RUN_COMMAND, str)` | Constructor now sealed — use `new ClickEvent.RunCommand(str)` |
| `ClickEvent.Action.RUN_COMMAND` | `ClickEvent.RunCommand` (record type) |
| `ClickEvent.Action.SUGGEST_COMMAND` | `ClickEvent.SuggestCommand` (record type) |
| `ClickEvent.Action.OPEN_URL` | `ClickEvent.OpenUrl` (record type) |
| `ClickEvent.Action.COPY_TO_CLIPBOARD` | `ClickEvent.CopyToClipboard` (record type) |

> **Note**: In MC 26.x, `ClickEvent` was refactored to use sealed subclasses/records instead of an `Action` enum. Each action is now its own class (e.g. `ClickEvent.RunCommand`, `ClickEvent.OpenUrl`).

### HoverEvent

| Old (Yarn) | New (Official) |
|---|---|
| `HoverEvent(HoverEvent.Action.SHOW_TEXT, text)` | `new HoverEvent.ShowText(component)` |
| `HoverEvent.Action.SHOW_TEXT` | `HoverEvent.ShowText` (record) |
| `HoverEvent.Action.SHOW_ITEM` | `HoverEvent.ShowItem` (record) |
| `HoverEvent.Action.SHOW_ENTITY` | `HoverEvent.ShowEntity` (record) |

### Formatting / ChatFormatting

| Old | New |
|---|---|
| `import net.minecraft.util.Formatting` | `import net.minecraft.ChatFormatting` |
| `Formatting.RED` | `ChatFormatting.RED` |
| `Formatting.GREEN` | `ChatFormatting.GREEN` |
| `Formatting.GOLD` | `ChatFormatting.GOLD` |
| `Formatting.GRAY` | `ChatFormatting.GRAY` |
| `Formatting.BOLD` | `ChatFormatting.BOLD` |
| `Formatting.ITALIC` | `ChatFormatting.ITALIC` |
| `text.formatted(Formatting.X)` | `component.withStyle(ChatFormatting.X)` |

### WorldSavePath / LevelResource

| Old | New |
|---|---|
| `import net.minecraft.util.WorldSavePath` | `import net.minecraft.world.level.storage.LevelResource` |
| `WorldSavePath.ROOT` | `LevelResource.ROOT` |
| `WorldSavePath.PLAYERS` | `LevelResource.PLAYER_DATA_DIR` *(verify)* |
| `server.getSavePath(WorldSavePath.ROOT)` | `server.getWorldPath(LevelResource.ROOT)` *(verify method name)* |

### MinecraftServer methods

| Old (Yarn) | New (Official) |
|---|---|
| `server.getSavePath(WorldSavePath.ROOT)` | `server.getWorldPath(LevelResource.ROOT)` |
| `server.getPlayerManager()` | `server.getPlayerList()` |
| `server.playerManager` (Kotlin) | `server.playerList` |
| `server.getOverworld()` | `server.overworld()` |
| `server.worlds` (iterable) | `server.getAllLevels()` |
| `server.commandManager.parseAndExecute(src, cmd)` | `server.getCommands().performPrefixedCommand(src, cmd)` *(verify)* |
| `server.commandManager.executeWithPrefix(src, cmd)` | `server.getCommands().performPrefixedCommand(src, cmd)` |
| `server.asCoroutineDispatcher()` | Same (Kotlin coroutine extension from fabric-language-kotlin) |
| `server.running` | `server.isRunning()` |
| `server.stopped` | `server.isStopped()` |

### ServerWorld / ServerLevel

| Old | New |
|---|---|
| `import net.minecraft.server.world.ServerWorld` | `import net.minecraft.server.level.ServerLevel` |
| `world.savingDisabled` | `world.noSave` |
| `world.registryKey` | `world.dimension()` → returns `ResourceKey<Level>` |
| `world.server.getSavePath(WorldSavePath.ROOT)` | `world.getServer().getWorldPath(LevelResource.ROOT)` |
| `world.server` | `world.getServer()` |

### DimensionType

| Old | New |
|---|---|
| `import net.minecraft.world.dimension.DimensionType` | `import net.minecraft.world.level.dimension.DimensionType` |
| `DimensionType.getSaveDirectory(registryKey, rootPath)` | Need to compute manually using `Level.dimension()` key name — see §5 |

### Util / OperatingSystem

`net.minecraft.util.Util` does **not exist** in MC 26.1.2 jar. Use:
- `Util.getOperatingSystem()` → Replace with Java's `System.getProperty("os.name")` and compare
- `Util.OperatingSystem.OSX` → `"mac"` in os.name
- `Util.OperatingSystem.LINUX` → `"linux"` in os.name

### PlayerManager / PlayerList

| Old | New |
|---|---|
| `import net.minecraft.server.PlayerManager` | `import net.minecraft.server.players.PlayerList` |
| `playerManager.broadcast(text, false)` | `playerList.broadcastSystemMessage(component, false)` |
| `playerManager.sendToAll(packet)` | `playerList.broadcastAll(packet)` |

### Network Packets

| Old | New |
|---|---|
| `import net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket` | `import net.minecraft.network.protocol.game.ClientboundTabListPacket` |
| `PlayerListHeaderS2CPacket(header, footer)` | `new ClientboundTabListPacket(header, footer)` |

### Client API

| Old | New |
|---|---|
| `MinecraftClient.getInstance()` | `Minecraft.getInstance()` |
| `client.options.language` | `client.options.languageCode` *(verify field name)* |
| `client.setScreen(screen)` | Same ✓ |
| `client.execute { ... }` | Same ✓ |
| `client.createIntegratedServerLoader()` | `client.createWorldOpenFlows()` *(verify)* |
| `loader.start(worldName) { ... }` | Verify new API |

### GUI Components

| Old | New |
|---|---|
| `ButtonWidget.builder(text, action).dimensions(...).build()` | `Button.builder(component, action).bounds(x,y,w,h).build()` |
| `import net.minecraft.client.gui.widget.ButtonWidget` | `import net.minecraft.client.gui.components.Button` |
| `DrawContext` | `GuiGraphics` |
| `context.drawCenteredTextWithShadow(renderer, text, x, y, color)` | `graphics.drawCenteredString(font, component, x, y, color)` |
| `context.fill(x1, y1, x2, y2, color)` | `graphics.fill(x1, y1, x2, y2, color)` ✓ same |
| `WorldListWidget` | `WorldSelectionList` |
| `SelectWorldScreen` | `SelectWorldScreen` ✓ same name, different package |
| `Screen(title)` constructor | `Screen(Component title)` ✓ same pattern |
| `screen.addDrawableChild(widget)` | `screen.addRenderableWidget(widget)` |
| `NativeImage` | `com.mojang.blaze3d.platform.NativeImage` |
| `NativeImageBackedTexture` | `net.minecraft.client.renderer.texture.DynamicTexture` |
| `ScreenTexts.OK` | `CommonComponents.GUI_OK` |

### Mixin Target Descriptors

Mixin `@At` `target` strings use internal class names that **also changed**:

| Old Descriptor | New Descriptor |
|---|---|
| `Lnet/minecraft/server/dedicated/DedicatedServerWatchdog;maxTickTime:J` | `Lnet/minecraft/server/dedicated/ServerWatchdog;maxTickTime:J` |
| `Lnet/minecraft/world/storage/StorageIoWorker;` | `Lnet/minecraft/world/level/chunk/storage/RegionFileStorage;` |
| `Lnet/minecraft/server/MinecraftServer;` | `Lnet/minecraft/server/MinecraftServer;` ✓ same |

---

## 4. Removed / Unavailable APIs

| Removed | Replacement Strategy |
|---|---|
| `net.minecraft.util.Util` (entire class) | No direct equivalent. `getOperatingSystem()` → use Java `System.getProperty("os.name")` |
| `Util.OperatingSystem` enum | Use String comparison on `System.getProperty("os.name").toLowerCase()` |
| `net.minecraft.util.Identifier` | Use `net.minecraft.resources.ResourceLocation` (may need `ResourceLocation.fromNamespaceAndPath(ns, path)`) |
| `Text.translatableWithFallback()` | `Component.translatableWithFallback()` — verify if present in 26.1.2 `Component` class |
| `StorageIoWorker` (Mixin target) | Target `RegionFileStorage` instead, or remove mixin if no longer needed |

---

## 5. File-by-File Port Guide

### `src/main/kotlin/.../XBackup.kt`

**Imports to change:**
```
// OLD → NEW
import net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket
  → import net.minecraft.network.protocol.game.ClientboundTabListPacket

import net.minecraft.server.command.ServerCommandSource
  → import net.minecraft.commands.CommandSourceStack

import net.minecraft.text.Text
  → import net.minecraft.network.chat.Component

import net.minecraft.util.Util
  → REMOVE (use Java OS detection)

import net.minecraft.util.WorldSavePath
  → import net.minecraft.world.level.storage.LevelResource
```

**Code changes:**
```kotlin
// Line 127-136: Util.getOperatingSystem()
// OLD:
when (Util.getOperatingSystem()) {
    Util.OperatingSystem.OSX, Util.OperatingSystem.LINUX -> { ... }
    else -> error(...)
}
// NEW:
val os = System.getProperty("os.name", "").lowercase()
when {
    os.contains("mac") || os.contains("nix") || os.contains("nux") -> { ... }
    else -> error(...)
}

// Line 163: server.getSavePath(WorldSavePath.ROOT)
// → server.getWorldPath(LevelResource.ROOT)

// Line 234-240: PlayerListHeaderS2CPacket / Text.empty()
// → ClientboundTabListPacket(Component.empty(), Component.literal(...))
// → server.playerList.broadcastAll(packet)

// Line 236-237: Text.empty(), Text.literal(...)
// → Component.empty(), Component.literal(...)

// Line 299: server.getSavePath(WorldSavePath.ROOT)
// → server.getWorldPath(LevelResource.ROOT)

// Line 396: ServerCommandSource param type
// → CommandSourceStack

// Line 401, 410: Text.of(...), source.sendError(Text.of(...))
// → Component.literal(...), source.sendFailure(Component.literal(...))
```

---

### `src/main/kotlin/.../Utils.kt`

**Imports to change:**
```
import net.minecraft.server.command.ServerCommandSource → import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.world.ServerWorld → import net.minecraft.server.level.ServerLevel
import net.minecraft.text.MutableText → import net.minecraft.network.chat.MutableComponent
import net.minecraft.text.Text → import net.minecraft.network.chat.Component
import net.minecraft.util.WorldSavePath → import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.dimension.DimensionType → import net.minecraft.world.level.dimension.DimensionType
```

**Code changes:**
```kotlin
// translate(): Text.translatableWithFallback → Component.translatableWithFallback
// Return type: MutableText → MutableComponent

// ServerCommandSource.send(): sendMessage(text) → sendSystemMessage(component)

// MinecraftServer.setAutoSaving: worlds → getAllLevels() / allLevels
// world.savingDisabled = !value → world.noSave = !value

// MinecraftServer.save(): saveAll() signature may differ

// MinecraftServer.finishRestore():
//   running = true → use server.setRunning(true) if setter exists
//   runServer() → verify method name

// MinecraftServer.broadcast: playerManager.broadcast → playerList.broadcastSystemMessage

// isFileInWorld():
//   DimensionType.getSaveDirectory(world.registryKey, ...) → compute from dimension().location()
//   world.registryKey → world.dimension() (returns ResourceKey<Level>)
//   world.server → world.getServer()
//   WorldSavePath.ROOT → LevelResource.ROOT
```

---

### `src/main/kotlin/.../Commands.kt`

**Imports to change:**
```
import net.minecraft.command.argument.ColumnPosArgumentType
  → import net.minecraft.commands.arguments.coordinates.ColumnPosArgument

import net.minecraft.server.command.ServerCommandSource
  → import net.minecraft.commands.CommandSourceStack

import net.minecraft.text.ClickEvent → import net.minecraft.network.chat.ClickEvent
import net.minecraft.text.HoverEvent → import net.minecraft.network.chat.HoverEvent
import net.minecraft.text.MutableText → import net.minecraft.network.chat.MutableComponent
import net.minecraft.text.Text → import net.minecraft.network.chat.Component

import net.minecraft.util.Formatting → import net.minecraft.ChatFormatting
import net.minecraft.util.Util → REMOVE
import net.minecraft.util.WorldSavePath → import net.minecraft.world.level.storage.LevelResource
```

**Code changes:**
```kotlin
// All Text.literal() → Component.literal()
// All Text.translatable() → Component.translatable()
// All Text.empty() → Component.empty()
// MutableText → MutableComponent
// .formatted(Formatting.X) → .withStyle(ChatFormatting.X)
// ClickEvent(ClickEvent.Action.RUN_COMMAND, str) → ClickEvent.RunCommand(str)
// ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, str) → ClickEvent.SuggestCommand(str)
// HoverEvent(HoverEvent.Action.SHOW_TEXT, text) → HoverEvent.ShowText(component)
// .styled { ... } / .withHoverEvent() / .withClickEvent()
//   → .withStyle(Style.EMPTY.withHoverEvent(...)) etc.
// ServerCommandSource → CommandSourceStack
// ColumnPosArgumentType → ColumnPosArgument
// WorldSavePath.ROOT → LevelResource.ROOT
```

---

### `src/main/kotlin/.../Task.kt`

```
import net.minecraft.text.Text → import net.minecraft.network.chat.Component
// val displayName: Text → val displayName: Component
```

---

### `src/main/kotlin/.../cloud/OnedriveSupport.kt`

```
import net.minecraft.text.ClickEvent → import net.minecraft.network.chat.ClickEvent
// Any ClickEvent usage → new sealed class API (ClickEvent.RunCommand, etc.)
```

---

### `src/main/kotlin/.../gui/BackupsGui.kt`

```
import net.minecraft.client.MinecraftClient → import net.minecraft.client.Minecraft
import net.minecraft.client.texture.NativeImage → import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.texture.NativeImageBackedTexture → import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.text.Text → import net.minecraft.network.chat.Component
import net.minecraft.util.Formatting → import net.minecraft.ChatFormatting
import net.minecraft.util.Identifier → import net.minecraft.resources.ResourceLocation
```

---

### `src/main/kotlin/.../gui/RestoreInfoScreen.kt`

```
import net.minecraft.client.MinecraftClient → import net.minecraft.client.Minecraft
import net.minecraft.client.gui.DrawContext → import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screen.Screen → import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.widget.ButtonWidget → import net.minecraft.client.gui.components.Button
import net.minecraft.text.Text → import net.minecraft.network.chat.Component
```

**Code changes:**
```kotlin
// Screen(Text.translatable(...)) → Screen(Component.translatable(...))
// ButtonWidget.builder(text, action).dimensions(...).build()
//   → Button.builder(component, action).bounds(x,y,w,h).build()
// context.drawCenteredTextWithShadow(textRenderer, text, x, y, color)
//   → guiGraphics.drawCenteredString(font, component, x, y, color)
// MinecraftClient.getInstance() → Minecraft.getInstance()
// client.createIntegratedServerLoader() → verify new API
// addDrawableChild(widget) → addRenderableWidget(widget)
```

---

### `src/main/java/.../mixin/MixinSelectWorldScreen.java`

```java
// Imports:
import net.minecraft.client.gui.DrawContext → import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screen.Screen → import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screen.world.SelectWorldScreen → import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
import net.minecraft.client.gui.screen.world.WorldListWidget → import net.minecraft.client.gui.screens.worldselection.WorldSelectionList
import net.minecraft.client.gui.widget.ButtonWidget → import net.minecraft.client.gui.components.Button
import net.minecraft.text.Text → import net.minecraft.network.chat.Component
import net.minecraft.util.Formatting → import net.minecraft.ChatFormatting
```

**Code changes:**
```java
// ButtonWidget → Button (same builder pattern)
// Text.literal("回") → Component.literal("回")
// Text.translatable(...) → Component.translatable(...)
// .formatted(Formatting.RED) → .withStyle(ChatFormatting.RED)
// WorldListWidget → WorldSelectionList  (field type)
// levelList.getSelectedAsOptional().get().level.getName()
//   → verify API for WorldSelectionList entry
// Screen(Text) constructor → Screen(Component)
// this.addDrawableChild(widget) → this.addRenderableWidget(widget)
// context.drawCenteredTextWithShadow → graphics.drawCenteredString
```

**Mixin @Mixin targets — no change** (SelectWorldScreen class name preserved).

---

### `src/main/java/.../mixin/disable/MixinDedicatedServerWatchdog.java`

```java
// OLD:
import net.minecraft.server.dedicated.DedicatedServerWatchdog;
@Mixin(DedicatedServerWatchdog.class)
// Field target: "Lnet/minecraft/server/dedicated/DedicatedServerWatchdog;maxTickTime:J"

// NEW:
import net.minecraft.server.dedicated.ServerWatchdog;
@Mixin(ServerWatchdog.class)
// Field target: "Lnet/minecraft/server/dedicated/ServerWatchdog;maxTickTime:J"
```

---

### `src/main/java/.../mixin/disable/MixinStorageIoWorker.java`

```java
// OLD: @Mixin(StorageIoWorker.class)
// StorageIoWorker moved/renamed.
// Target: net.minecraft.world.level.chunk.storage.RegionFileStorage (closest equivalent)
// This mixin is likely a stub (empty body) — may just need the import updated
// or the mixin can be removed if no longer needed.

// NEW (if needed):
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
@Mixin(RegionFileStorage.class)
```

---

### `src/main/java/.../mixin/MixinServer.java`

```java
// MinecraftServer class is SAME → no import change needed
// Method names to verify:
//   "save" method → verify still exists with same signature
//   "saveAll" method → verify
//   "shutdown" method → verify
// These may have changed signatures in 26.1.2
```

---

### `src/main/java/.../gui/OptionDialog.java`

```java
// import net.minecraft.screen.ScreenTexts → import net.minecraft.network.chat.CommonComponents
// import net.minecraft.text.Text → import net.minecraft.network.chat.Component
// import static net.minecraft.util.Formatting.GREEN → import net.minecraft.ChatFormatting (static import)

// ScreenTexts.OK.copy().formatted(GREEN)
//   → CommonComponents.GUI_OK.copy().withStyle(ChatFormatting.GREEN)
// OR: Component.translatable("gui.ok").withStyle(ChatFormatting.GREEN)

// Text params in method signatures → Component
```

---

## 6. Fabric API Changes for 26.1.2

These Fabric API modules remain available but their callback signatures now use Mojang official types:

| Fabric API | Old Type | New Type |
|---|---|---|
| `ServerLifecycleEvents.SERVER_STARTED` callback | `MinecraftServer` | `MinecraftServer` ✓ same |
| `CommandRegistrationCallback` dispatcher param | `CommandDispatcher<ServerCommandSource>` | `CommandDispatcher<CommandSourceStack>` |
| `ServerWorldEvents` world param | `ServerWorld` | `ServerLevel` |

> **Note**: `CommandRegistrationCallback { dispatcher, _, _ -> ... }` — the dispatcher type parameter changes to `CommandSourceStack`.

---

## 7. Mixin Configuration

The mixin JSON file (`src/main/resources/x_backup.mixins.json`) references class names for `@Mixin` targets. Since Mixin resolves targets at **runtime** by class name (not by Java package), the `@Mixin(TargetClass.class)` annotation in source drives resolution — the JSON config lists the mixin *class* names, not target names, so the JSON itself may not need changes. However, `@At(target=...)` string descriptors **must** be updated as described above.

---

## 8. Stonecutter Strategy

The project uses **Stonecutter** for multi-version support. For 26.1.2-specific code, use:

```kotlin
//? if >= 26.1.2 {
// Mojang official API code here
//?} else {
/* Yarn-mapped code here */
//?}
```

For imports that differ, use type aliases at the top of each file:

```kotlin
//? if >= 26.1.2 {
import net.minecraft.network.chat.Component as Text
import net.minecraft.network.chat.MutableComponent as MutableText
import net.minecraft.ChatFormatting as Formatting
//?} else {
/*
import net.minecraft.text.Text
import net.minecraft.text.MutableText
import net.minecraft.util.Formatting
*/
//?}
```

> **Caution**: Type alias approach only works if APIs are identical beyond the name. For methods that changed (e.g. `.formatted()` → `.withStyle()`), full code blocks must be conditioned.

---

## 9. Verified Mappings & Port Findings

Through direct inspection of `minecraft-merged.jar` for Minecraft 26.1.2 and resolution of compiler errors, the following mappings are confirmed:

### 9.1 Core Classes & Packages
* **`net.minecraft.util.Identifier`** (Yarn) → **`net.minecraft.resources.Identifier`** (MojMap). 
  * Note: It is NOT named `ResourceLocation`. The class name is `Identifier` and it has moved packages.
  * Static creators: `Identifier.parse(String)`, `Identifier.fromNamespaceAndPath(String, String)`.
* **`net.minecraft.client.gui.DrawContext`** (Yarn) → **`net.minecraft.client.gui.GuiGraphicsExtractor`** (MojMap).
  * Render methods on screens override `extractRenderState(GuiGraphicsExtractor, int, int, float)`.
  * Background rendering uses `extractBackground(GuiGraphicsExtractor, int, int, float)`.
  * Text rendering on `GuiGraphicsExtractor` uses `text(...)` and `centeredText(...)` instead of `drawString` or `drawCenteredString`.
* **`net.minecraft.client.gui.screen.world.WorldListWidget`** (Yarn) → **`net.minecraft.client.gui.screens.worldselection.WorldSelectionList`** (MojMap).
  * Entry class is `WorldSelectionList.Entry`.
  * Get selected entry: `levelList.getSelectedOpt()` (returns `Optional<Entry>`).
  * Get level summary from entry: `entry.getLevelSummary()` (returns `LevelSummary`).
* **`net.minecraft.world.level.storage.LevelSummary`**:
  * Folder name / ID of world: `getLevelId()`.
  * Display name of world: `getLevelName()`.
* **`net.minecraft.client.gui.components.Tooltip`**:
  * Tooltip creation: `Tooltip.create(Component)`.
* **`net.minecraft.client.gui.widget.ButtonWidget`** (Yarn) → **`net.minecraft.client.gui.components.Button`** (MojMap).
  * Added to screen via `addRenderableWidget(...)` (formerly `addDrawableChild`).
  * Bounds set in builder: `Button.builder(...).bounds(x, y, width, height).build()`.

### 9.2 Server Lifecycle & Threading
* **`MinecraftServer.isOnThread`** (Yarn) → **`MinecraftServer.isSameThread()`** (MojMap).
* **`MinecraftServer.networkIo`** (Yarn) → **`MinecraftServer.connection`** (field) / **`getConnection()`** (method) returning `ServerConnectionListener`.
  * Listing connections: `connection.getConnections()`.
* **`MinecraftServer.thread`** (Yarn) → **`MinecraftServer.serverThread`** (field) / **`getRunningThread()`** (method).
* **`MinecraftServer.save()`** (Yarn) → **`MinecraftServer.saveEverything(suppressLog, flush, force)`** (MojMap).
* **`MinecraftServer.shutdown()`** (Yarn) → **`MinecraftServer.stopServer()`** (MojMap).
* **`Connection.disconnected`** (Yarn) → **`Connection.disconnectionHandled`** (field).
  * Setting `connection.disconnectionHandled = true` prevents save-on-disconnect during restore.
  * Disconnecting connection: `connection.disconnect(Component)`.
* **`DimensionType.getSaveDirectory()`** (Yarn) → **`DimensionType.getStorageFolder(ResourceKey<Level>, Path)`** (MojMap).

### 9.3 Permissions & Command Sources
* **`Permissions.check(source, perm, defaultLevel)`**:
  * Under MojMap 26.1.2, `CommandSourceStack` does NOT implement `net.minecraft.commands.CommandSource`.
  * To check permissions using the stub, pass `source.source` (which is `CommandSource`).
  * Stub classes package: `net.minecraft.commands.CommandSource` instead of `net.minecraft.command.CommandSource`.
* **`CommandSourceStack.permissions`**:
  * The `permissions` field is private. Use the public method `permissions()` returning `PermissionSet`.
  * Checking a permission level: `source.permissions().hasPermission(new Permission.HasCommandLevel(permissionLevel))`.
* **`CommandSourceStack.name`** (Yarn) → **`CommandSourceStack.textName`** (field) / **`getTextName()`** (getter).
* **`CommandSourceStack.world`** (Yarn) → **`CommandSourceStack.level`** (field) / **`getLevel()`** (getter).
* **`CommandSourceStack.sendError(Text)`** (Yarn) → **`CommandSourceStack.sendFailure(Component)`** (MojMap).

---

*This document was updated by directly inspecting `minecraft-merged.jar` for MC 26.1.2 and analyzing compilation failures.*
