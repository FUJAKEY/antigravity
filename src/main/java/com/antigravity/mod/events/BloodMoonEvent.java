package com.antigravity.mod.events;

import com.antigravity.mod.AntigravityMod;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.monster.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * Blood Moon Event Handler
 * Manages the Blood Moon event cycle - a periodic nightmare event where monsters
 * become stronger, more spawn, and sanity drains faster.
 */
@Mod.EventBusSubscriber(modid = AntigravityMod.MOD_ID)
public class BloodMoonEvent {
    
    private static final Map<String, BloodMoonState> worldStates = new HashMap<>();
    private static final Random random = new Random();
    
    // Configuration
    private static final int BLOOD_MOON_DURATION = 12000; // One full night
    private static final int BLOOD_MOON_CYCLE = 168000; // Every ~7 in-game days
    private static final double SPAWN_MULTIPLIER = 3.0;
    private static final double DAMAGE_MULTIPLIER = 1.5;
    private static final double SANITY_DRAIN_MULTIPLIER = 2.0;
    
    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.world instanceof ServerWorld)) return;
        
        ServerWorld world = (ServerWorld) event.world;
        BloodMoonState state = getOrCreateState(world);
        
        state.tick(world);
    }
    
    private static BloodMoonState getOrCreateState(ServerWorld world) {
        String key = world.dimension().location().toString();
        return worldStates.computeIfAbsent(key, k -> new BloodMoonState());
    }
    
    public static boolean isBloodMoonActive(World world) {
        String key = world.dimension().location().toString();
        BloodMoonState state = worldStates.get(key);
        return state != null && state.isActive();
    }
    
    public static double getIntensity(World world) {
        String key = world.dimension().location().toString();
        BloodMoonState state = worldStates.get(key);
        return state != null ? state.getIntensity() : 0.0;
    }
    
    /**
     * State tracker for Blood Moon in a specific world.
     */
    public static class BloodMoonState {
        private boolean active = false;
        private int activeTimer = 0;
        private int cooldownTimer = 0;
        private double intensity = 0.0;
        private BloodMoonPhase phase = BloodMoonPhase.DORMANT;
        private final BloodMoonEffectManager effectManager = new BloodMoonEffectManager();
        private final MonsterWaveController waveController = new MonsterWaveController();
        
        public void tick(ServerWorld world) {
            // Don't trigger in peaceful
            if (world.getDifficulty() == Difficulty.PEACEFUL) {
                deactivate();
                return;
            }
            
            if (active) {
                tickActive(world);
            } else {
                tickDormant(world);
            }
        }
        
        private void tickDormant(ServerWorld world) {
            cooldownTimer++;
            
            // Check if it's night and time for blood moon
            if (cooldownTimer >= BLOOD_MOON_CYCLE && isNight(world)) {
                activate(world);
            }
        }
        
        private void tickActive(ServerWorld world) {
            activeTimer++;
            
            // Update intensity based on phase
            updateIntensity();
            
            // Apply effects
            effectManager.tick(world, this);
            waveController.tick(world, this);
            
            // Affect all players
            for (ServerPlayerEntity player : world.players()) {
                applyPlayerEffects(player);
            }
            
            // Check for end
            if (activeTimer >= BLOOD_MOON_DURATION || !isNight(world)) {
                deactivate();
            }
        }
        
        private void updateIntensity() {
            // Intensity ramps up from 0 to 1 over the first quarter,
            // stays at 1 for the middle half, then decreases
            double progress = (double) activeTimer / BLOOD_MOON_DURATION;
            
            if (progress < 0.25) {
                intensity = progress * 4.0;
                phase = BloodMoonPhase.RISING;
            } else if (progress < 0.75) {
                intensity = 1.0;
                phase = BloodMoonPhase.PEAK;
            } else {
                intensity = (1.0 - progress) * 4.0;
                phase = BloodMoonPhase.WANING;
            }
        }
        
        private void applyPlayerEffects(ServerPlayerEntity player) {
            // Visual indicator
            if (activeTimer == 1) {
                player.displayClientMessage(
                    new StringTextComponent("The Blood Moon rises...").withStyle(TextFormatting.DARK_RED),
                    true
                );
            }
            
            // Apply night vision for eerie red tint effect (client-side shader would be ideal)
            // Apply weakness at peak intensity
            if (phase == BloodMoonPhase.PEAK && activeTimer % 100 == 0) {
                player.addEffect(new EffectInstance(Effects.WEAKNESS, 200, 0));
            }
        }
        
        private void activate(ServerWorld world) {
            active = true;
            activeTimer = 0;
            intensity = 0.0;
            phase = BloodMoonPhase.RISING;
            
            // Announce to all players
            for (ServerPlayerEntity player : world.players()) {
                player.displayClientMessage(
                    new StringTextComponent("A Blood Moon has risen!").withStyle(TextFormatting.DARK_RED, TextFormatting.BOLD),
                    false
                );
            }
            
            waveController.reset();
        }
        
        private void deactivate() {
            active = false;
            cooldownTimer = 0;
            intensity = 0.0;
            phase = BloodMoonPhase.DORMANT;
        }
        
        private boolean isNight(ServerWorld world) {
            long time = world.getDayTime() % 24000;
            return time >= 13000 && time < 23000;
        }
        
        public boolean isActive() { return active; }
        public double getIntensity() { return intensity; }
        public BloodMoonPhase getPhase() { return phase; }
    }
    
    public enum BloodMoonPhase {
        DORMANT,
        RISING,
        PEAK,
        WANING
    }
    
    /**
     * Manages special effects during Blood Moon.
     */
    public static class BloodMoonEffectManager {
        private int effectCounter = 0;
        
        public void tick(ServerWorld world, BloodMoonState state) {
            effectCounter++;
            
            // Periodic lightning strikes
            if (state.getIntensity() > 0.5 && effectCounter % 400 == 0) {
                strikeLightning(world);
            }
            
            // Extinguish torches near players at peak
            if (state.getPhase() == BloodMoonPhase.PEAK && effectCounter % 200 == 0) {
                extinguishNearbyTorches(world);
            }
        }
        
        private void strikeLightning(ServerWorld world) {
            List<ServerPlayerEntity> players = world.players();
            if (players.isEmpty()) return;
            
            PlayerEntity target = players.get(random.nextInt(players.size()));
            BlockPos strikePos = target.blockPosition().offset(
                random.nextInt(32) - 16,
                0,
                random.nextInt(32) - 16
            );
            
            // Find surface
            strikePos = world.getHeightmapPos(
                net.minecraft.world.gen.Heightmap.Type.MOTION_BLOCKING,
                strikePos
            );
            
            // Create lightning entity
            net.minecraft.entity.effect.LightningBoltEntity lightning = 
                EntityType.LIGHTNING_BOLT.create(world);
            if (lightning != null) {
                lightning.moveTo(strikePos.getX(), strikePos.getY(), strikePos.getZ());
                world.addFreshEntity(lightning);
            }
        }
        
        private void extinguishNearbyTorches(ServerWorld world) {
            for (ServerPlayerEntity player : world.players()) {
                BlockPos center = player.blockPosition();
                int radius = 16;
                
                for (int x = -radius; x <= radius; x++) {
                    for (int y = -radius; y <= radius; y++) {
                        for (int z = -radius; z <= radius; z++) {
                            BlockPos pos = center.offset(x, y, z);
                            if (world.getBlockState(pos).getBlock() == net.minecraft.block.Blocks.TORCH) {
                                if (random.nextDouble() < 0.1) {
                                    world.removeBlock(pos, false);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Controls monster wave spawning during Blood Moon.
     */
    public static class MonsterWaveController {
        private int waveNumber = 0;
        private int waveTimer = 0;
        private int monstersThisWave = 0;
        
        private static final int WAVE_INTERVAL = 1200; // 1 minute between waves
        private static final EntityType<?>[] WAVE_MONSTERS = {
            EntityType.ZOMBIE,
            EntityType.SKELETON,
            EntityType.SPIDER,
            EntityType.CREEPER,
            EntityType.WITCH,
            EntityType.PHANTOM
        };
        
        public void reset() {
            waveNumber = 0;
            waveTimer = 0;
            monstersThisWave = 0;
        }
        
        public void tick(ServerWorld world, BloodMoonState state) {
            waveTimer++;
            
            if (waveTimer >= WAVE_INTERVAL) {
                spawnWave(world, state);
                waveTimer = 0;
                waveNumber++;
            }
        }
        
        private void spawnWave(ServerWorld world, BloodMoonState state) {
            for (ServerPlayerEntity player : world.players()) {
                int spawnCount = (int) (5 + waveNumber * 2 * state.getIntensity());
                monstersThisWave = 0;
                
                for (int i = 0; i < spawnCount; i++) {
                    spawnMonsterNearPlayer(world, player);
                }
                
                player.displayClientMessage(
                    new StringTextComponent("Wave " + (waveNumber + 1) + " - " + monstersThisWave + " creatures approach!")
                        .withStyle(TextFormatting.RED),
                    true
                );
            }
        }
        
        private void spawnMonsterNearPlayer(ServerWorld world, PlayerEntity player) {
            EntityType<?> type = WAVE_MONSTERS[random.nextInt(WAVE_MONSTERS.length)];
            
            // Find spawn position
            BlockPos playerPos = player.blockPosition();
            int attempts = 10;
            
            for (int i = 0; i < attempts; i++) {
                int offsetX = random.nextInt(32) - 16;
                int offsetZ = random.nextInt(32) - 16;
                
                // Keep minimum distance
                if (Math.abs(offsetX) < 10) offsetX = offsetX < 0 ? -10 : 10;
                if (Math.abs(offsetZ) < 10) offsetZ = offsetZ < 0 ? -10 : 10;
                
                BlockPos spawnPos = playerPos.offset(offsetX, 0, offsetZ);
                spawnPos = world.getHeightmapPos(
                    net.minecraft.world.gen.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                    spawnPos
                );
                
                // Check spawn conditions
                if (world.getBlockState(spawnPos.below()).getMaterial().isSolid() &&
                    world.getBlockState(spawnPos).isAir()) {
                    
                    LivingEntity entity = (LivingEntity) type.spawn(
                        world, null, null, spawnPos, SpawnReason.EVENT, false, false
                    );
                    
                    if (entity != null) {
                        // Buff the monster
                        applyBloodMoonBuffs(entity);
                        monstersThisWave++;
                        return;
                    }
                }
            }
        }
        
        private void applyBloodMoonBuffs(LivingEntity entity) {
            // Speed boost
            entity.addEffect(new EffectInstance(Effects.MOVEMENT_SPEED, 12000, 1));
            
            // Resistance to damage
            entity.addEffect(new EffectInstance(Effects.DAMAGE_RESISTANCE, 12000, 0));
            
            // Strength
            entity.addEffect(new EffectInstance(Effects.DAMAGE_BOOST, 12000, 0));
        }
        
        public int getWaveNumber() { return waveNumber; }
    }
}
