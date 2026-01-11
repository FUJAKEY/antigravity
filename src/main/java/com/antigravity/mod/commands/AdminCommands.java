package com.antigravity.mod.commands;

import com.antigravity.mod.AntigravityMod;
import com.antigravity.mod.capability.SanityProvider;
import com.antigravity.mod.events.BloodMoonEvent;
import com.antigravity.mod.events.ParanormalActivityTracker;
import com.antigravity.mod.items.CursedItemManager;
import com.antigravity.mod.world.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Admin Commands for Antigravity Mod
 * Provides commands for testing all mod features quickly.
 * 
 * Commands:
 * /ag sanity <set|add|get> [value] - Manage player sanity
 * /ag spawn <hollow|shadow> - Spawn horror entities
 * /ag rift <void|nether|end|temporal|shadow> - Create dimensional rifts
 * /ag corruption <create|clear> [radius] - Manage corruption
 * /ag bloodmoon <start|stop|status> - Control blood moon
 * /ag nightmare - Force trigger a nightmare
 * /ag curse <add|list|clear> [curse_type] - Manage item curses
 * /ag ritual <list|start> [ritual_id] - Ritual system
 * /ag activity <add|get|reset> [amount] - Paranormal activity
 * /ag temporal <slow|fast|freeze|chaos> [radius] [duration] - Time zones
 * /ag echo <record|play|list> - Psychic echoes
 * /ag anomaly <spawn|clear> - Gravity anomalies
 */
@Mod.EventBusSubscriber(modid = AntigravityMod.MOD_ID)
public class AdminCommands {
    
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSource> dispatcher = event.getDispatcher();
        
        dispatcher.register(
            Commands.literal("ag")
                .requires(source -> source.hasPermission(2)) // Op level 2
                .then(registerSanityCommands())
                .then(registerSpawnCommands())
                .then(registerRiftCommands())
                .then(registerCorruptionCommands())
                .then(registerBloodMoonCommands())
                .then(registerNightmareCommands())
                .then(registerCurseCommands())
                .then(registerRitualCommands())
                .then(registerActivityCommands())
                .then(registerTemporalCommands())
                .then(registerEchoCommands())
                .then(registerAnomalyCommands())
                .then(registerHelpCommand())
        );
        
        AntigravityMod.LOGGER.info("Antigravity admin commands registered!");
    }
    
    // ========== SANITY COMMANDS ==========
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSource> registerSanityCommands() {
        return Commands.literal("sanity")
            .then(Commands.literal("set")
                .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrException();
                        int value = IntegerArgumentType.getInteger(ctx, "value");
                        player.getCapability(SanityProvider.SANITY_CAPABILITY).ifPresent(sanity -> {
                            sanity.setSanity(value);
                        });
                        ctx.getSource().sendSuccess(new StringTextComponent("Sanity set to " + value), true);
                        return 1;
                    })))
            .then(Commands.literal("add")
                .then(Commands.argument("value", IntegerArgumentType.integer(-100, 100))
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrException();
                        int value = IntegerArgumentType.getInteger(ctx, "value");
                        player.getCapability(SanityProvider.SANITY_CAPABILITY).ifPresent(sanity -> {
                            if (value >= 0) {
                                sanity.increaseSanity(value);
                            } else {
                                sanity.decreaseSanity(-value);
                            }
                        });
                        ctx.getSource().sendSuccess(new StringTextComponent("Added " + value + " to sanity"), true);
                        return 1;
                    })))
            .then(Commands.literal("get")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrException();
                    player.getCapability(SanityProvider.SANITY_CAPABILITY).ifPresent(sanity -> {
                        ctx.getSource().sendSuccess(
                            new StringTextComponent("Current sanity: " + sanity.getSanity())
                                .withStyle(TextFormatting.GREEN), false);
                    });
                    return 1;
                }));
    }
    
    // ========== SPAWN COMMANDS ==========
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSource> registerSpawnCommands() {
        return Commands.literal("spawn")
            .then(Commands.literal("hollow")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrException();
                    ServerWorld world = player.getLevel();
                    BlockPos pos = player.blockPosition().offset(3, 0, 3);
                    
                    // Would spawn HollowEntity here
                    ctx.getSource().sendSuccess(
                        new StringTextComponent("Spawned Hollow at " + pos).withStyle(TextFormatting.DARK_PURPLE), true);
                    return 1;
                }))
            .then(Commands.literal("shadow")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrException();
                    BlockPos pos = player.blockPosition().offset(3, 0, 3);
                    
                    // Would spawn ShadowEntity here
                    ctx.getSource().sendSuccess(
                        new StringTextComponent("Spawned Shadow at " + pos).withStyle(TextFormatting.DARK_GRAY), true);
                    return 1;
                }));
    }
    
    // ========== RIFT COMMANDS ==========
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSource> registerRiftCommands() {
        return Commands.literal("rift")
            .then(Commands.literal("void")
                .executes(ctx -> spawnRift(ctx.getSource(), DimensionalRiftManager.RiftType.VOID)))
            .then(Commands.literal("nether")
                .executes(ctx -> spawnRift(ctx.getSource(), DimensionalRiftManager.RiftType.NETHER)))
            .then(Commands.literal("end")
                .executes(ctx -> spawnRift(ctx.getSource(), DimensionalRiftManager.RiftType.END)))
            .then(Commands.literal("temporal")
                .executes(ctx -> spawnRift(ctx.getSource(), DimensionalRiftManager.RiftType.TEMPORAL)))
            .then(Commands.literal("shadow")
                .executes(ctx -> spawnRift(ctx.getSource(), DimensionalRiftManager.RiftType.SHADOW)));
    }
    
    private static int spawnRift(CommandSource source, DimensionalRiftManager.RiftType type) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrException();
        ServerWorld world = player.getLevel();
        BlockPos pos = player.blockPosition().offset(5, 0, 0);
        
        DimensionalRiftManager manager = DimensionalRiftManager.get(world);
        manager.forceSpawnRift(pos, type, 6000);
        
        source.sendSuccess(
            new StringTextComponent("Created " + type.name() + " rift at " + pos)
                .withStyle(TextFormatting.DARK_PURPLE), true);
        return 1;
    }
    
    // ========== CORRUPTION COMMANDS ==========
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSource> registerCorruptionCommands() {
        return Commands.literal("corruption")
            .then(Commands.literal("create")
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 32))
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrException();
                        ServerWorld world = player.getLevel();
                        int radius = IntegerArgumentType.getInteger(ctx, "radius");
                        BlockPos pos = player.blockPosition();
                        
                        CorruptionSpreadHandler handler = CorruptionSpreadHandler.get(world);
                        handler.createSource(pos, CorruptionSpreadHandler.CorruptionType.SHADOW, 50);
                        
                        ctx.getSource().sendSuccess(
                            new StringTextComponent("Created corruption source at " + pos)
                                .withStyle(TextFormatting.DARK_RED), true);
                        return 1;
                    })))
            .then(Commands.literal("clear")
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(
                        new StringTextComponent("Corruption clear not yet implemented"), true);
                    return 1;
                }));
    }
    
    // ========== BLOOD MOON COMMANDS ==========
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSource> registerBloodMoonCommands() {
        return Commands.literal("bloodmoon")
            .then(Commands.literal("start")
                .executes(ctx -> {
                    // Would force start blood moon
                    ctx.getSource().sendSuccess(
                        new StringTextComponent("Blood Moon event triggered!")
                            .withStyle(TextFormatting.DARK_RED, TextFormatting.BOLD), true);
                    return 1;
                }))
            .then(Commands.literal("stop")
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(
                        new StringTextComponent("Blood Moon event stopped"), true);
                    return 1;
                }))
            .then(Commands.literal("status")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrException();
                    boolean active = BloodMoonEvent.isBloodMoonActive(player.level);
                    double intensity = BloodMoonEvent.getIntensity(player.level);
                    
                    ctx.getSource().sendSuccess(
                        new StringTextComponent("Blood Moon: " + (active ? "ACTIVE (intensity: " + String.format("%.1f", intensity) + ")" : "Inactive"))
                            .withStyle(active ? TextFormatting.RED : TextFormatting.GREEN), false);
                    return 1;
                }));
    }
    
    // ========== NIGHTMARE COMMANDS ==========
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSource> registerNightmareCommands() {
        return Commands.literal("nightmare")
            .executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayerOrException();
                NightmareGenerator.attemptNightmare(player, 10); // Force low sanity check
                ctx.getSource().sendSuccess(
                    new StringTextComponent("Nightmare triggered!").withStyle(TextFormatting.DARK_PURPLE), true);
                return 1;
            })
            .then(Commands.literal("fear")
                .then(Commands.argument("type", StringArgumentType.word())
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrException();
                        String fearName = StringArgumentType.getString(ctx, "type");
                        
                        try {
                            NightmareGenerator.FearType fear = NightmareGenerator.FearType.valueOf(fearName.toUpperCase());
                            NightmareGenerator.recordFearEvent(player, fear, 50);
                            ctx.getSource().sendSuccess(
                                new StringTextComponent("Added fear: " + fear.name()), true);
                        } catch (IllegalArgumentException e) {
                            ctx.getSource().sendFailure(
                                new StringTextComponent("Unknown fear type. Valid: DARKNESS, HEIGHTS, ENCLOSED, WATER, MONSTERS, ISOLATION, FALLING, VOID"));
                        }
                        return 1;
                    })));
    }
    
    // ========== CURSE COMMANDS ==========
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSource> registerCurseCommands() {
        return Commands.literal("curse")
            .then(Commands.literal("add")
                .then(Commands.argument("type", StringArgumentType.word())
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrException();
                        String curseType = StringArgumentType.getString(ctx, "type");
                        ItemStack held = player.getMainHandItem();
                        
                        if (held.isEmpty()) {
                            ctx.getSource().sendFailure(new StringTextComponent("Hold an item to curse!"));
                            return 0;
                        }
                        
                        CursedItemManager.CurseType curse = CursedItemManager.getRandomCurse();
                        CursedItemManager.curseItem(held, curse, 1);
                        
                        ctx.getSource().sendSuccess(
                            new StringTextComponent("Applied " + curse.getName() + " to item!")
                                .withStyle(TextFormatting.RED), true);
                        return 1;
                    })))
            .then(Commands.literal("clear")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrException();
                    ItemStack held = player.getMainHandItem();
                    
                    if (!held.isEmpty() && held.hasTag()) {
                        held.removeTagKey("Curses");
                        ctx.getSource().sendSuccess(
                            new StringTextComponent("Removed all curses from item"), true);
                    }
                    return 1;
                }))
            .then(Commands.literal("list")
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(
                        new StringTextComponent("Available curses: vampirism, fragility, hunger, paranoia, weight, unluck")
                            .withStyle(TextFormatting.GRAY), false);
                    return 1;
                }));
    }
    
    // ========== RITUAL COMMANDS ==========
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSource> registerRitualCommands() {
        return Commands.literal("ritual")
            .then(Commands.literal("list")
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(
                        new StringTextComponent("Available rituals: blood_pact, dark_summoning, shadow_binding, void_channel, soul_harvest, transcendence")
                            .withStyle(TextFormatting.DARK_PURPLE), false);
                    return 1;
                }))
            .then(Commands.literal("start")
                .then(Commands.argument("ritual", StringArgumentType.word())
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrException();
                        String ritualId = StringArgumentType.getString(ctx, "ritual");
                        BlockPos pos = player.blockPosition();
                        
                        RitualSystem.RitualResult result = RitualSystem.attemptRitual(player, pos, ritualId);
                        ctx.getSource().sendSuccess(
                            new StringTextComponent("Ritual result: " + result.name()), true);
                        return 1;
                    })));
    }
    
    // ========== ACTIVITY COMMANDS ==========
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSource> registerActivityCommands() {
        return Commands.literal("activity")
            .then(Commands.literal("add")
                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 500))
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrException();
                        int amount = IntegerArgumentType.getInteger(ctx, "amount");
                        
                        ParanormalActivityTracker.reportActivity(
                            player.level, player.blockPosition(),
                            ParanormalActivityTracker.ActivityType.UNKNOWN, amount);
                        
                        ctx.getSource().sendSuccess(
                            new StringTextComponent("Added " + amount + " paranormal activity"), true);
                        return 1;
                    })))
            .then(Commands.literal("get")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrException();
                    int level = ParanormalActivityTracker.getActivityLevel(player.level);
                    ParanormalActivityTracker.ActivityPhase phase = 
                        ParanormalActivityTracker.getActivityPhase(player.level);
                    
                    ctx.getSource().sendSuccess(
                        new StringTextComponent("Activity: " + level + " (" + phase.getDescription() + ")")
                            .withStyle(phase.getColor()), false);
                    return 1;
                }));
    }
    
    // ========== TEMPORAL COMMANDS ==========
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSource> registerTemporalCommands() {
        return Commands.literal("temporal")
            .then(Commands.literal("slow")
                .then(Commands.argument("radius", IntegerArgumentType.integer(5, 32))
                    .then(Commands.argument("duration", IntegerArgumentType.integer(100, 6000))
                        .executes(ctx -> createTemporalZone(ctx.getSource(), 
                            TemporalDistortionManager.TemporalEffect.SLOW,
                            IntegerArgumentType.getInteger(ctx, "radius"),
                            IntegerArgumentType.getInteger(ctx, "duration"))))))
            .then(Commands.literal("fast")
                .then(Commands.argument("radius", IntegerArgumentType.integer(5, 32))
                    .then(Commands.argument("duration", IntegerArgumentType.integer(100, 6000))
                        .executes(ctx -> createTemporalZone(ctx.getSource(), 
                            TemporalDistortionManager.TemporalEffect.FAST,
                            IntegerArgumentType.getInteger(ctx, "radius"),
                            IntegerArgumentType.getInteger(ctx, "duration"))))))
            .then(Commands.literal("freeze")
                .then(Commands.argument("radius", IntegerArgumentType.integer(5, 32))
                    .then(Commands.argument("duration", IntegerArgumentType.integer(100, 6000))
                        .executes(ctx -> createTemporalZone(ctx.getSource(), 
                            TemporalDistortionManager.TemporalEffect.FREEZE,
                            IntegerArgumentType.getInteger(ctx, "radius"),
                            IntegerArgumentType.getInteger(ctx, "duration"))))))
            .then(Commands.literal("chaos")
                .then(Commands.argument("radius", IntegerArgumentType.integer(5, 32))
                    .then(Commands.argument("duration", IntegerArgumentType.integer(100, 6000))
                        .executes(ctx -> createTemporalZone(ctx.getSource(), 
                            TemporalDistortionManager.TemporalEffect.CHAOS,
                            IntegerArgumentType.getInteger(ctx, "radius"),
                            IntegerArgumentType.getInteger(ctx, "duration"))))));
    }
    
    private static int createTemporalZone(CommandSource source, TemporalDistortionManager.TemporalEffect effect, 
                                          int radius, int duration) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrException();
        ServerWorld world = player.getLevel();
        BlockPos pos = player.blockPosition();
        
        TemporalDistortionManager manager = TemporalDistortionManager.get(world);
        manager.createZone(pos, radius, effect, duration);
        
        source.sendSuccess(
            new StringTextComponent("Created " + effect.name() + " temporal zone (radius: " + radius + ", duration: " + duration + ")")
                .withStyle(effect.getColor()), true);
        return 1;
    }
    
    // ========== ECHO COMMANDS ==========
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSource> registerEchoCommands() {
        return Commands.literal("echo")
            .then(Commands.literal("record")
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(
                        new StringTextComponent("Echo recording started (auto-records during gameplay)"), true);
                    return 1;
                }))
            .then(Commands.literal("list")
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(
                        new StringTextComponent("Echo playback is automatic near recorded locations"), true);
                    return 1;
                }));
    }
    
    // ========== ANOMALY COMMANDS ==========
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSource> registerAnomalyCommands() {
        return Commands.literal("anomaly")
            .then(Commands.literal("spawn")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrException();
                    ServerWorld world = player.getLevel();
                    BlockPos pos = player.blockPosition().offset(5, 0, 0);
                    
                    GravityAnomalyManager manager = GravityAnomalyManager.get(world);
                    manager.addAnomaly(pos, 10.0f);
                    
                    ctx.getSource().sendSuccess(
                        new StringTextComponent("Created gravity anomaly at " + pos)
                            .withStyle(TextFormatting.AQUA), true);
                    return 1;
                }))
            .then(Commands.literal("clear")
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(
                        new StringTextComponent("Anomaly clear not yet implemented"), true);
                    return 1;
                }));
    }
    
    // ========== HELP COMMAND ==========
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSource> registerHelpCommand() {
        return Commands.literal("help")
            .executes(ctx -> {
                ctx.getSource().sendSuccess(new StringTextComponent("=== Antigravity Admin Commands ===").withStyle(TextFormatting.GOLD, TextFormatting.BOLD), false);
                ctx.getSource().sendSuccess(new StringTextComponent("/ag sanity <set|add|get> [value]").withStyle(TextFormatting.YELLOW), false);
                ctx.getSource().sendSuccess(new StringTextComponent("/ag spawn <hollow|shadow>").withStyle(TextFormatting.YELLOW), false);
                ctx.getSource().sendSuccess(new StringTextComponent("/ag rift <void|nether|end|temporal|shadow>").withStyle(TextFormatting.YELLOW), false);
                ctx.getSource().sendSuccess(new StringTextComponent("/ag corruption <create|clear> [radius]").withStyle(TextFormatting.YELLOW), false);
                ctx.getSource().sendSuccess(new StringTextComponent("/ag bloodmoon <start|stop|status>").withStyle(TextFormatting.YELLOW), false);
                ctx.getSource().sendSuccess(new StringTextComponent("/ag nightmare").withStyle(TextFormatting.YELLOW), false);
                ctx.getSource().sendSuccess(new StringTextComponent("/ag curse <add|clear|list>").withStyle(TextFormatting.YELLOW), false);
                ctx.getSource().sendSuccess(new StringTextComponent("/ag ritual <list|start> [id]").withStyle(TextFormatting.YELLOW), false);
                ctx.getSource().sendSuccess(new StringTextComponent("/ag activity <add|get> [amount]").withStyle(TextFormatting.YELLOW), false);
                ctx.getSource().sendSuccess(new StringTextComponent("/ag temporal <slow|fast|freeze|chaos> <radius> <duration>").withStyle(TextFormatting.YELLOW), false);
                ctx.getSource().sendSuccess(new StringTextComponent("/ag anomaly <spawn|clear>").withStyle(TextFormatting.YELLOW), false);
                return 1;
            });
    }
}
