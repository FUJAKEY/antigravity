package com.antigravity.mod.world;

import com.antigravity.mod.AntigravityMod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.server.ServerWorld;

import java.util.*;

/**
 * Ritual System
 * Allows players to perform dark rituals with specific requirements.
 * Rituals have costs, requirements, and powerful effects - some beneficial, some dangerous.
 */
public class RitualSystem {
    
    private static final Map<String, RitualType> registeredRituals = new HashMap<>();
    private static final Map<UUID, ActiveRitual> activeRituals = new HashMap<>();
    private static final Random random = new Random();
    
    static {
        // Register all ritual types
        registerRitual(new RitualType.BloodPact());
        registerRitual(new RitualType.DarkSummoning());
        registerRitual(new RitualType.ShadowBinding());
        registerRitual(new RitualType.VoidChannel());
        registerRitual(new RitualType.SoulHarvest());
        registerRitual(new RitualType.Transcendence());
    }
    
    public static void registerRitual(RitualType ritual) {
        registeredRituals.put(ritual.getId(), ritual);
    }
    
    /**
     * Attempts to start a ritual at the given location.
     */
    public static RitualResult attemptRitual(ServerPlayerEntity player, BlockPos center, String ritualId) {
        RitualType ritual = registeredRituals.get(ritualId);
        if (ritual == null) {
            return RitualResult.UNKNOWN_RITUAL;
        }
        
        // Check if player already has an active ritual
        if (activeRituals.containsKey(player.getUUID())) {
            return RitualResult.ALREADY_ACTIVE;
        }
        
        // Validate ritual circle
        RitualCircleValidator validator = new RitualCircleValidator(player.level, center);
        if (!validator.validate(ritual)) {
            return RitualResult.INVALID_CIRCLE;
        }
        
        // Check material requirements
        if (!ritual.checkMaterials(player)) {
            return RitualResult.MISSING_MATERIALS;
        }
        
        // Check lunar phase requirements
        if (!ritual.checkLunarPhase((ServerWorld) player.level)) {
            return RitualResult.WRONG_PHASE;
        }
        
        // Start the ritual
        ActiveRitual active = new ActiveRitual(ritual, player, center);
        activeRituals.put(player.getUUID(), active);
        
        player.displayClientMessage(
            new StringTextComponent("The ritual begins...").withStyle(TextFormatting.DARK_PURPLE),
            false
        );
        
        return RitualResult.STARTED;
    }
    
    /**
     * Ticks all active rituals.
     */
    public static void tick(ServerWorld world) {
        Iterator<Map.Entry<UUID, ActiveRitual>> iterator = activeRituals.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveRitual> entry = iterator.next();
            ActiveRitual ritual = entry.getValue();
            
            RitualTickResult result = ritual.tick(world);
            
            if (result == RitualTickResult.COMPLETE || result == RitualTickResult.FAILED) {
                iterator.remove();
            }
        }
    }
    
    /**
     * Cancels a player's active ritual.
     */
    public static void cancelRitual(UUID playerId, boolean interrupted) {
        ActiveRitual ritual = activeRituals.remove(playerId);
        if (ritual != null && interrupted) {
            ritual.onInterrupted();
        }
    }
    
    public enum RitualResult {
        STARTED,
        UNKNOWN_RITUAL,
        ALREADY_ACTIVE,
        INVALID_CIRCLE,
        MISSING_MATERIALS,
        WRONG_PHASE
    }
    
    public enum RitualTickResult {
        CONTINUE,
        COMPLETE,
        FAILED
    }
    
    /**
     * Represents an ongoing ritual.
     */
    public static class ActiveRitual {
        private final RitualType type;
        private final ServerPlayerEntity caster;
        private final BlockPos center;
        private int progress = 0;
        private boolean consumed = false;
        private final RitualEffectRenderer renderer;
        
        public ActiveRitual(RitualType type, ServerPlayerEntity caster, BlockPos center) {
            this.type = type;
            this.caster = caster;
            this.center = center;
            this.renderer = new RitualEffectRenderer(center);
        }
        
        public RitualTickResult tick(ServerWorld world) {
            progress++;
            
            // Check if caster moved too far
            if (caster.distanceToSqr(center.getX(), center.getY(), center.getZ()) > 25) {
                return RitualTickResult.FAILED;
            }
            
            // Consume materials at start
            if (!consumed) {
                type.consumeMaterials(caster);
                consumed = true;
            }
            
            // Apply ongoing effects
            type.tickEffect(caster, progress);
            
            // Render effects
            renderer.tick(world, progress, type);
            
            // Check completion
            if (progress >= type.getDuration()) {
                type.complete(caster, center);
                return RitualTickResult.COMPLETE;
            }
            
            return RitualTickResult.CONTINUE;
        }
        
        public void onInterrupted() {
            // Backlash damage
            caster.hurt(DamageSource.MAGIC, 10);
            caster.displayClientMessage(
                new StringTextComponent("The ritual recoils!").withStyle(TextFormatting.RED),
                false
            );
            
            // Negative effects
            caster.addEffect(new EffectInstance(Effects.WEAKNESS, 600, 1));
            caster.addEffect(new EffectInstance(Effects.BLINDNESS, 100, 0));
        }
    }
    
    /**
     * Base class for ritual types.
     */
    public static abstract class RitualType {
        protected final String id;
        protected final String name;
        protected final int duration;
        protected final Map<Item, Integer> requiredMaterials = new HashMap<>();
        protected final List<Block> circleBlocks = new ArrayList<>();
        protected int requiredMoonPhase = -1; // -1 = any phase
        
        public RitualType(String id, String name, int duration) {
            this.id = id;
            this.name = name;
            this.duration = duration;
        }
        
        public abstract void complete(ServerPlayerEntity caster, BlockPos center);
        public abstract void tickEffect(ServerPlayerEntity caster, int progress);
        
        public boolean checkMaterials(PlayerEntity player) {
            for (Map.Entry<Item, Integer> entry : requiredMaterials.entrySet()) {
                int count = countItems(player, entry.getKey());
                if (count < entry.getValue()) return false;
            }
            return true;
        }
        
        public void consumeMaterials(PlayerEntity player) {
            for (Map.Entry<Item, Integer> entry : requiredMaterials.entrySet()) {
                removeItems(player, entry.getKey(), entry.getValue());
            }
        }
        
        public boolean checkLunarPhase(ServerWorld world) {
            if (requiredMoonPhase < 0) return true;
            return world.getMoonPhase() == requiredMoonPhase;
        }
        
        private int countItems(PlayerEntity player, Item item) {
            int count = 0;
            for (int i = 0; i < player.inventory.getContainerSize(); i++) {
                ItemStack stack = player.inventory.getItem(i);
                if (stack.getItem() == item) {
                    count += stack.getCount();
                }
            }
            return count;
        }
        
        private void removeItems(PlayerEntity player, Item item, int amount) {
            int remaining = amount;
            for (int i = 0; i < player.inventory.getContainerSize() && remaining > 0; i++) {
                ItemStack stack = player.inventory.getItem(i);
                if (stack.getItem() == item) {
                    int remove = Math.min(remaining, stack.getCount());
                    stack.shrink(remove);
                    remaining -= remove;
                }
            }
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public int getDuration() { return duration; }
        public List<Block> getCircleBlocks() { return circleBlocks; }
        
        /**
         * Blood Pact - regeneration at the cost of max health.
         */
        public static class BloodPact extends RitualType {
            public BloodPact() {
                super("blood_pact", "Blood Pact", 200);
                requiredMaterials.put(Items.GHAST_TEAR, 1);
                requiredMaterials.put(Items.BLAZE_POWDER, 4);
                circleBlocks.add(Blocks.NETHERRACK);
            }
            
            @Override
            public void complete(ServerPlayerEntity caster, BlockPos center) {
                // Give powerful regeneration
                caster.addEffect(new EffectInstance(Effects.REGENERATION, 6000, 2));
                caster.addEffect(new EffectInstance(Effects.HEALTH_BOOST, 6000, 1));
                
                // But reduce current health
                caster.hurt(DamageSource.MAGIC, caster.getHealth() * 0.2f);
                
                caster.displayClientMessage(
                    new StringTextComponent("Your blood sings with power!").withStyle(TextFormatting.DARK_RED),
                    false
                );
            }
            
            @Override
            public void tickEffect(ServerPlayerEntity caster, int progress) {
                // Drain health during ritual
                if (progress % 20 == 0) {
                    caster.hurt(DamageSource.MAGIC, 1);
                }
            }
        }
        
        /**
         * Dark Summoning - summon hostile mobs.
         */
        public static class DarkSummoning extends RitualType {
            public DarkSummoning() {
                super("dark_summoning", "Dark Summoning", 300);
                requiredMaterials.put(Items.BONE, 8);
                requiredMaterials.put(Items.ROTTEN_FLESH, 8);
                circleBlocks.add(Blocks.OBSIDIAN);
            }
            
            @Override
            public void complete(ServerPlayerEntity caster, BlockPos center) {
                // Summon some zombies and skeletons
                for (int i = 0; i < 5; i++) {
                    double x = center.getX() + random.nextInt(6) - 3;
                    double z = center.getZ() + random.nextInt(6) - 3;
                    
                    EntityType<?> type = random.nextBoolean() ? EntityType.ZOMBIE : EntityType.SKELETON;
                    type.spawn((ServerWorld) caster.level, null, null, 
                        new BlockPos(x, center.getY() + 1, z), SpawnReason.MOB_SUMMONED, false, false);
                }
                
                caster.displayClientMessage(
                    new StringTextComponent("The dead rise at your command!").withStyle(TextFormatting.GRAY),
                    false
                );
            }
            
            @Override
            public void tickEffect(ServerPlayerEntity caster, int progress) {
                // Darkness effect during summoning
                if (progress % 40 == 0) {
                    caster.addEffect(new EffectInstance(Effects.BLINDNESS, 30, 0));
                }
            }
        }
        
        /**
         * Shadow Binding - become invisible but vulnerable.
         */
        public static class ShadowBinding extends RitualType {
            public ShadowBinding() {
                super("shadow_binding", "Shadow Binding", 150);
                requiredMaterials.put(Items.COAL, 16);
                requiredMaterials.put(Items.INK_SAC, 4);
                circleBlocks.add(Blocks.BLACK_WOOL);
            }
            
            @Override
            public void complete(ServerPlayerEntity caster, BlockPos center) {
                caster.addEffect(new EffectInstance(Effects.INVISIBILITY, 3600, 0));
                caster.addEffect(new EffectInstance(Effects.WEAKNESS, 3600, 0));
                
                caster.displayClientMessage(
                    new StringTextComponent("You fade into the shadows...").withStyle(TextFormatting.DARK_GRAY),
                    false
                );
            }
            
            @Override
            public void tickEffect(ServerPlayerEntity caster, int progress) {
                // Silence effect
            }
        }
        
        /**
         * Void Channel - teleport to spawn.
         */
        public static class VoidChannel extends RitualType {
            public VoidChannel() {
                super("void_channel", "Void Channel", 400);
                requiredMaterials.put(Items.ENDER_PEARL, 4);
                requiredMaterials.put(Items.CHORUS_FRUIT, 8);
                circleBlocks.add(Blocks.END_STONE);
            }
            
            @Override
            public void complete(ServerPlayerEntity caster, BlockPos center) {
                // Teleport to world spawn
                BlockPos spawn = ((ServerWorld) caster.level).getSharedSpawnPos();
                caster.teleportTo(spawn.getX(), spawn.getY(), spawn.getZ());
                
                caster.displayClientMessage(
                    new StringTextComponent("The void carries you home...").withStyle(TextFormatting.DARK_PURPLE),
                    false
                );
            }
            
            @Override
            public void tickEffect(ServerPlayerEntity caster, int progress) {
                if (progress % 30 == 0) {
                    caster.addEffect(new EffectInstance(Effects.LEVITATION, 20, 0));
                }
            }
        }
        
        /**
         * Soul Harvest - gain XP from all nearby entities.
         */
        public static class SoulHarvest extends RitualType {
            public SoulHarvest() {
                super("soul_harvest", "Soul Harvest", 250);
                requiredMaterials.put(Items.SOUL_SAND, 4);
                requiredMaterials.put(Items.WITHER_ROSE, 1);
                circleBlocks.add(Blocks.SOUL_SAND);
            }
            
            @Override
            public void complete(ServerPlayerEntity caster, BlockPos center) {
                // Give large XP boost
                caster.giveExperiencePoints(500);
                
                caster.displayClientMessage(
                    new StringTextComponent("Souls flow into you!").withStyle(TextFormatting.GREEN),
                    false
                );
            }
            
            @Override
            public void tickEffect(ServerPlayerEntity caster, int progress) {
                // Wither effect during ritual
                if (progress % 40 == 0) {
                    caster.addEffect(new EffectInstance(Effects.WITHER, 30, 0));
                }
            }
        }
        
        /**
         * Transcendence - ultimate power at great cost.
         */
        public static class Transcendence extends RitualType {
            public Transcendence() {
                super("transcendence", "Transcendence", 600);
                requiredMaterials.put(Items.NETHER_STAR, 1);
                requiredMaterials.put(Items.GOLDEN_APPLE, 1);
                circleBlocks.add(Blocks.GOLD_BLOCK);
                requiredMoonPhase = 0; // Full moon only
            }
            
            @Override
            public void complete(ServerPlayerEntity caster, BlockPos center) {
                // Ultimate buff
                caster.addEffect(new EffectInstance(Effects.DAMAGE_RESISTANCE, 6000, 1));
                caster.addEffect(new EffectInstance(Effects.DAMAGE_BOOST, 6000, 2));
                caster.addEffect(new EffectInstance(Effects.MOVEMENT_SPEED, 6000, 1));
                caster.addEffect(new EffectInstance(Effects.REGENERATION, 6000, 1));
                
                // But at a permanent cost (would need capability to track)
                caster.displayClientMessage(
                    new StringTextComponent("You have transcended mortal limits!").withStyle(TextFormatting.GOLD, TextFormatting.BOLD),
                    false
                );
            }
            
            @Override
            public void tickEffect(ServerPlayerEntity caster, int progress) {
                // Intense pain during ritual
                if (progress % 60 == 0) {
                    caster.hurt(DamageSource.MAGIC, 2);
                }
            }
        }
    }
    
    /**
     * Validates ritual circle placement.
     */
    public static class RitualCircleValidator {
        private final ServerWorld world;
        private final BlockPos center;
        
        public RitualCircleValidator(ServerWorld world, BlockPos center) {
            this.world = (ServerWorld) world;
            this.center = center;
        }
        
        public RitualCircleValidator(net.minecraft.world.World world, BlockPos center) {
            this.world = world instanceof ServerWorld ? (ServerWorld) world : null;
            this.center = center;
        }
        
        public boolean validate(RitualType ritual) {
            if (ritual.getCircleBlocks().isEmpty()) return true;
            
            // Check for circle pattern around center
            Block requiredBlock = ritual.getCircleBlocks().get(0);
            int radius = 3;
            
            int matchCount = 0;
            int requiredCount = 0;
            
            // Check cardinal and diagonal positions
            BlockPos[] positions = {
                center.north(radius),
                center.south(radius),
                center.east(radius),
                center.west(radius),
                center.north(radius).east(radius),
                center.north(radius).west(radius),
                center.south(radius).east(radius),
                center.south(radius).west(radius)
            };
            
            for (BlockPos pos : positions) {
                requiredCount++;
                if (world.getBlockState(pos).getBlock() == requiredBlock) {
                    matchCount++;
                }
            }
            
            return matchCount >= requiredCount * 0.75;
        }
    }
    
    /**
     * Renders visual effects during rituals.
     */
    public static class RitualEffectRenderer {
        private final BlockPos center;
        
        public RitualEffectRenderer(BlockPos center) {
            this.center = center;
        }
        
        public void tick(ServerWorld world, int progress, RitualType type) {
            // Spawn particles in a circle
            double angle = (progress * 0.1) % (Math.PI * 2);
            double radius = 3.0;
            
            for (int i = 0; i < 4; i++) {
                double theta = angle + (i * Math.PI / 2);
                double x = center.getX() + 0.5 + Math.cos(theta) * radius;
                double y = center.getY() + 1.0;
                double z = center.getZ() + 0.5 + Math.sin(theta) * radius;
                
                world.sendParticles(
                    net.minecraft.particles.ParticleTypes.FLAME,
                    x, y, z, 1, 0, 0.05, 0, 0
                );
            }
            
            // Center particles
            world.sendParticles(
                net.minecraft.particles.ParticleTypes.ENCHANT,
                center.getX() + 0.5, center.getY() + 1.5, center.getZ() + 0.5,
                5, 0.3, 0.5, 0.3, 0.1
            );
        }
    }
}
