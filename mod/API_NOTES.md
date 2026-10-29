# Minecraft and Fabric APIs used by the EVOLVE mod

Target: **Minecraft Java 26.1.2** (unobfuscated, Mojang's official names),
Fabric Loader and Fabric API, Java 25.

The mod could not be compiled against the real game when it was written: the
build environment had no access to Mojang's or Fabric's servers. Every
signature below is what the code assumes. The code was type-checked against
stand-in stubs of exactly these signatures (zero errors), so a CI failure
should come from one of these assumptions, not from typos.

Version-sensitive calls live in `McCompat.java`. Fixing one usually means
editing that file only.

Confidence: **high** = unchanged for many versions; **medium** = recently
renamed or reshaped, so verify first if the build fails; **low** = a guess.

## Checked against other 26.1.2 code

Mojang's servers were unreachable while this was written, so each assumption
was checked against open-source code built for Minecraft 26.1.2: Fabric API
0.155.3+26.1.2 (FabricMC/fabric, branch 26.1.2), Fabric's example mod
(branch 26.1.2), NeoForge (branch 26.1.x, which includes patches quoting
Minecraft's own source) and Carpet (branch 26.1).

| # | Assumption | Where | Status |
|---|---|---|---|
| 1 | `net.minecraft.resources.Identifier.fromNamespaceAndPath(String, String)` (`ResourceLocation` was renamed in 1.21.11) | `McCompat.id` | confirmed: Fabric's 26.1.2 example mod |
| 2 | `new AttributeModifier(Identifier, double, AttributeModifier.Operation)` | `McCompat.setMultiplier` | confirmed: Fabric API item test mod |
| 3 | `AttributeInstance#addTransientModifier(AttributeModifier)` and `#removeModifier(Identifier)` | `McCompat.setMultiplier` | add confirmed (NeoForge patch of `EnderMan`); remove by id assumed |
| 4 | `Level#hasChunkAt(BlockPos)`: true only for loaded chunks | `McCompat.isChunkLoaded` | confirmed: Carpet |
| 5 | `ServerLevel#getSeed()` | `McCompat.seed` | confirmed: Carpet |
| 6 | `MinecraftServer#getWorldPath(LevelResource.ROOT)` | `McCompat.worldDir` | confirmed: Carpet |
| 7 | `Attributes.GRAVITY`, `FALL_DAMAGE_MULTIPLIER`, `SAFE_FALL_DISTANCE` | `McCompat.setRegionGravity` | assumed (vanilla since 1.20.5; not used by the reference code) |
| 8 | `BlockState#canBeReplaced()` (no arguments, vanilla since 1.20) | `WorldAccess.isOpen`, jobs | assumed; fallback `state.is(BlockTags.REPLACEABLE)` |
| 9 | `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)` for admin commands | `EvolveCommands` | confirmed: NeoForge commands |
| 10 | `ChunkPos` is a record in 26.1 (`x()`, `z()`), not fields | not used; chunk coordinates are computed with `>> 4` | n/a |

## Fabric API

| API | Assumed signature | Confidence |
|---|---|---|
| `net.fabricmc.api.ModInitializer` | `void onInitialize()` | high |
| `net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED` | `Event<ServerStarted>`, `void onServerStarted(MinecraftServer)` | high |
| `ServerLifecycleEvents.SERVER_STOPPING` | `Event<ServerStopping>`, `void onServerStopping(MinecraftServer)` | high |
| `net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK` | `Event<EndTick>`, `void onEndTick(MinecraftServer)` | high |
| `net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT` | `register(CommandDispatcher<CommandSourceStack>, CommandBuildContext, Commands.CommandSelection)`; used as a 3-argument lambda, so the parameter types are never named | high |
| `net.fabricmc.fabric.api.event.Event#register(T)` | | high |

None of these classes contain Yarn-specific names, so the Fabric API rename
pass for 26.1 should not touch them.

## Brigadier and SLF4J (shipped with Minecraft)

| API | Confidence |
|---|---|
| `CommandDispatcher#register(LiteralArgumentBuilder<S>)` | high |
| `ArgumentBuilder#then(ArgumentBuilder)`, `#executes(Command<S>)` | high |
| `StringArgumentType.greedyString()`, `StringArgumentType.getString(CommandContext<?>, String)` | high |
| `IntegerArgumentType.integer(int, int)`, `IntegerArgumentType.getInteger(CommandContext<?>, String)` | high |
| `LongArgumentType.longArg(long)`, `LongArgumentType.getLong(CommandContext<?>, String)` | high |
| `org.slf4j.LoggerFactory.getLogger(String)`, `Logger.info/warn/error` | high |

## Minecraft

| API | Assumed signature | Confidence |
|---|---|---|
| `MinecraftServer#overworld()` | `ServerLevel` | high |
| `MinecraftServer#getPlayerList()` | `PlayerList` | high |
| `PlayerList#getPlayers()` | `List<ServerPlayer>` | high |
| `PlayerList#broadcastSystemMessage(Component, boolean overlay)` | | high |
| `Level#getHeight(Heightmap.Types, int x, int z)` | One above the top block. Returns the minimum Y for unloaded chunks and never loads them; the mod checks the chunk is loaded first anyway. | high |
| `Level#getBlockState(BlockPos)` / `Level#setBlock(BlockPos, BlockState, int flags)` | Flags `3` = UPDATE_NEIGHBORS, UPDATE_CLIENTS | high |
| `Level#getBiome(BlockPos)` | `Holder<Biome>`. For unloaded chunks the biome comes from the generator without loading the chunk; used once for 1024 cells at `/evolve start`. | high (the no-load behaviour: medium) |
| `Entity#level()` | `Level`. Only compared by identity with the overworld, so a covariant `ServerLevel` return also compiles. | high |
| `Entity#blockPosition()` | `BlockPos` | high |
| `LivingEntity#getAttribute(Holder<Attribute>)` | nullable `AttributeInstance` | high |
| `CommandSourceStack#getPlayer()` | nullable `ServerPlayer` | high |
| `CommandSourceStack#sendSuccess(Supplier<Component>, boolean)` / `#sendFailure(Component)` | | high |
| `Commands.literal(String)` / `Commands.argument(String, ArgumentType<T>)` | | high |
| `Component.literal(String)` | `MutableComponent`; `MutableComponent#withStyle(ChatFormatting)`, `#append(Component)` | high |
| `ChatFormatting` | AQUA, DARK_GRAY, DARK_GREEN, GOLD, GRAY, GREEN, LIGHT_PURPLE, RED, WHITE | high |
| `BlockPos` | public `(int, int, int)` constructor, `offset(int,int,int)`, `above()`, `north()`, `south()`, `east()`, `west()`, `asLong()`, `immutable()`, `getX/Y/Z()` | high |
| `BlockPos.MutableBlockPos` | `set(int, int, int)` returning itself | high |
| `Holder#is(TagKey<T>)` / `Holder#is(ResourceKey<T>)` | | high |
| `BlockState` | `is(TagKey<Block>)`, `is(Block)`, `isAir()`, `getFluidState()`; `FluidState#isEmpty()` | high |
| `Block#defaultBlockState()` | | high |
| `BlockTags` | LOGS, LEAVES, SAPLINGS, DIRT | high |
| `BiomeTags` | IS_OCEAN, IS_DEEP_OCEAN, IS_RIVER, IS_BADLANDS, IS_BEACH, IS_JUNGLE, IS_TAIGA, IS_FOREST, IS_SAVANNA, IS_MOUNTAIN, IS_HILL | high |
| `Biomes` (`ResourceKey<Biome>`) | SWAMP, MANGROVE_SWAMP, DESERT, CHERRY_GROVE, BIRCH_FOREST, OLD_GROWTH_BIRCH_FOREST, DARK_FOREST, MEADOW | high |
| `Heightmap.Types.WORLD_SURFACE` | | high |

`Blocks` constants used (all long-standing; the newest are the cherry set
from 1.20 and the mangrove and mud sets from 1.19): AIR, WATER, GRASS_BLOCK,
DIRT, COARSE_DIRT, PODZOL, MYCELIUM, ROOTED_DIRT, DIRT_PATH, FARMLAND, MOSS_BLOCK,
WHEAT, CARROTS, POTATOES, HAY_BLOCK, COMPOSTER, the planks, logs, slabs and saplings
of oak, spruce, birch, jungle, acacia, dark oak, cherry and mangrove (plus
MANGROVE_PROPAGULE), COBBLESTONE, COBBLESTONE_SLAB, MOSSY_COBBLESTONE,
MOSSY_COBBLESTONE_SLAB, STONE_BRICKS, STONE_BRICK_SLAB, CRACKED_STONE_BRICKS,
MOSSY_STONE_BRICKS, MOSSY_STONE_BRICK_SLAB, SMOOTH_STONE, MUD_BRICKS,
MUD_BRICK_SLAB, PACKED_MUD, GLASS, LANTERN, CRAFTING_TABLE, CHEST, BARREL, BELL,
LECTERN, WHITE_BANNER.

## Deliberately avoided

- **Mixins and access wideners**: nothing in the mod needs engine internals.
- **Fabric's new permission API** (`fabric-permission-api-v1`): vanilla's
  `Commands.hasPermission` is enough for game-master-only commands.
- **NBT and `SavedData`**: their APIs changed repeatedly in 1.21.x. The
  simulation saves itself as JSON through the core's `SaveManager`, into
  `<world>/evolve/state.json`.
- **Multi-block or connected blocks** (doors, beds, fences, panes, stairs):
  they need placement context or neighbour-aware states. Structures use
  doorways, glass blocks, slabs and full blocks instead.
- **Client code**: the mod is server-side, so vanilla clients can join. The
  gravity attribute is synced to clients by the game; the mod assumes it is
  marked syncable, as it affects client-side movement prediction.
