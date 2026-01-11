package com.antigravity.mod.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import java.util.*;

/**
 * Nightmare Generator
 * Generates procedural nightmare sequences for players based on their actions,
 * fears, and sanity level. Creates personalized horror experiences.
 */
public class NightmareGenerator {
    
    private static final Map<UUID, PlayerNightmareProfile> playerProfiles = new HashMap<>();
    private static final Random random = new Random();
    
    /**
     * Gets or creates a nightmare profile for a player.
     */
    public static PlayerNightmareProfile getProfile(PlayerEntity player) {
        return playerProfiles.computeIfAbsent(player.getUUID(), 
            uuid -> new PlayerNightmareProfile(uuid));
    }
    
    /**
     * Record a fear-inducing event for the player.
     */
    public static void recordFearEvent(PlayerEntity player, FearType type, double intensity) {
        PlayerNightmareProfile profile = getProfile(player);
        profile.recordFear(type, intensity);
    }
    
    /**
     * Attempt to trigger a nightmare sequence for a player.
     */
    public static void attemptNightmare(ServerPlayerEntity player, double sanityLevel) {
        PlayerNightmareProfile profile = getProfile(player);
        
        // Lower sanity = higher nightmare chance
        double nightmareChance = (100 - sanityLevel) / 100.0 * 0.1;
        
        if (random.nextDouble() < nightmareChance) {
            NightmareSequence sequence = profile.generateNightmare();
            sequence.execute(player);
        }
    }
    
    /**
     * Profile tracking a player's fears and nightmare history.
     */
    public static class PlayerNightmareProfile {
        private final UUID playerId;
        private final Map<FearType, Double> fearLevels = new EnumMap<>(FearType.class);
        private final List<NightmareRecord> nightmareHistory = new ArrayList<>();
        private final FearAnalyzer analyzer = new FearAnalyzer();
        
        public PlayerNightmareProfile(UUID id) {
            this.playerId = id;
            
            // Initialize fear levels
            for (FearType type : FearType.values()) {
                fearLevels.put(type, 0.0);
            }
        }
        
        public void recordFear(FearType type, double intensity) {
            double current = fearLevels.getOrDefault(type, 0.0);
            fearLevels.put(type, Math.min(100.0, current + intensity));
            
            analyzer.analyze(this);
        }
        
        public NightmareSequence generateNightmare() {
            // Find the player's dominant fear
            FearType dominantFear = getDominantFear();
            
            // Generate a nightmare based on that fear
            NightmareSequence sequence = new NightmareSequence(dominantFear);
            
            // Build the sequence
            sequence.addPhase(new NightmarePhase.ApproachPhase(dominantFear));
            sequence.addPhase(new NightmarePhase.BuildupPhase(dominantFear));
            sequence.addPhase(new NightmarePhase.ClimaxPhase(dominantFear));
            sequence.addPhase(new NightmarePhase.ResolutionPhase(dominantFear));
            
            return sequence;
        }
        
        public FearType getDominantFear() {
            FearType dominant = FearType.DARKNESS;
            double maxLevel = 0;
            
            for (Map.Entry<FearType, Double> entry : fearLevels.entrySet()) {
                if (entry.getValue() > maxLevel) {
                    maxLevel = entry.getValue();
                    dominant = entry.getKey();
                }
            }
            
            return dominant;
        }
        
        public void recordNightmare(NightmareRecord record) {
            nightmareHistory.add(record);
            
            // Limit history size
            while (nightmareHistory.size() > 50) {
                nightmareHistory.remove(0);
            }
        }
        
        public double getFearLevel(FearType type) {
            return fearLevels.getOrDefault(type, 0.0);
        }
        
        public void decayFears(double amount) {
            for (FearType type : FearType.values()) {
                double current = fearLevels.getOrDefault(type, 0.0);
                fearLevels.put(type, Math.max(0, current - amount));
            }
        }
    }
    
    /**
     * Types of fears that can be tracked.
     */
    public enum FearType {
        DARKNESS("You fear the dark"),
        HEIGHTS("You fear heights"),
        ENCLOSED("You fear enclosed spaces"),
        WATER("You fear deep water"),
        MONSTERS("You fear creatures"),
        ISOLATION("You fear being alone"),
        FALLING("You fear falling"),
        VOID("You fear the void");
        
        private final String description;
        
        FearType(String desc) {
            this.description = desc;
        }
        
        public String getDescription() { return description; }
    }
    
    /**
     * Analyzes player fears to predict effective nightmare patterns.
     */
    public static class FearAnalyzer {
        private final Map<FearType, List<FearType>> fearCorrelations = new EnumMap<>(FearType.class);
        
        public FearAnalyzer() {
            // Define fear correlations
            fearCorrelations.put(FearType.DARKNESS, Arrays.asList(FearType.MONSTERS, FearType.ISOLATION));
            fearCorrelations.put(FearType.HEIGHTS, Arrays.asList(FearType.FALLING, FearType.VOID));
            fearCorrelations.put(FearType.ENCLOSED, Arrays.asList(FearType.DARKNESS, FearType.ISOLATION));
            fearCorrelations.put(FearType.WATER, Arrays.asList(FearType.VOID, FearType.FALLING));
            fearCorrelations.put(FearType.MONSTERS, Arrays.asList(FearType.DARKNESS, FearType.ISOLATION));
            fearCorrelations.put(FearType.ISOLATION, Arrays.asList(FearType.DARKNESS, FearType.VOID));
            fearCorrelations.put(FearType.FALLING, Arrays.asList(FearType.HEIGHTS, FearType.VOID));
            fearCorrelations.put(FearType.VOID, Arrays.asList(FearType.ISOLATION, FearType.FALLING));
        }
        
        public void analyze(PlayerNightmareProfile profile) {
            // When one fear increases, related fears get a boost
            FearType dominantFear = profile.getDominantFear();
            List<FearType> related = fearCorrelations.get(dominantFear);
            
            if (related != null) {
                for (FearType type : related) {
                    double current = profile.getFearLevel(type);
                    double boost = profile.getFearLevel(dominantFear) * 0.1;
                    profile.fearLevels.put(type, Math.min(100.0, current + boost));
                }
            }
        }
        
        public double calculateNightmareIntensity(PlayerNightmareProfile profile) {
            double total = 0;
            for (FearType type : FearType.values()) {
                total += profile.getFearLevel(type);
            }
            return Math.min(1.0, total / 400.0);
        }
    }
    
    /**
     * A complete nightmare sequence with multiple phases.
     */
    public static class NightmareSequence {
        private final FearType primaryFear;
        private final List<NightmarePhase> phases = new ArrayList<>();
        private int currentPhase = 0;
        private int phaseTimer = 0;
        private boolean active = false;
        
        public NightmareSequence(FearType fear) {
            this.primaryFear = fear;
        }
        
        public void addPhase(NightmarePhase phase) {
            phases.add(phase);
        }
        
        public void execute(ServerPlayerEntity player) {
            active = true;
            currentPhase = 0;
            phaseTimer = 0;
            
            player.displayClientMessage(
                new StringTextComponent("Something stirs in your mind...")
                    .withStyle(TextFormatting.DARK_PURPLE, TextFormatting.ITALIC),
                true
            );
            
            if (!phases.isEmpty()) {
                phases.get(0).begin(player);
            }
        }
        
        public void tick(ServerPlayerEntity player) {
            if (!active || currentPhase >= phases.size()) return;
            
            NightmarePhase phase = phases.get(currentPhase);
            phaseTimer++;
            
            phase.tick(player, phaseTimer);
            
            if (phase.isComplete(phaseTimer)) {
                phase.end(player);
                currentPhase++;
                phaseTimer = 0;
                
                if (currentPhase < phases.size()) {
                    phases.get(currentPhase).begin(player);
                } else {
                    active = false;
                }
            }
        }
        
        public boolean isActive() { return active; }
    }
    
    /**
     * Base class for nightmare phases.
     */
    public static abstract class NightmarePhase {
        protected final FearType fear;
        protected int duration;
        
        public NightmarePhase(FearType fear, int duration) {
            this.fear = fear;
            this.duration = duration;
        }
        
        public abstract void begin(ServerPlayerEntity player);
        public abstract void tick(ServerPlayerEntity player, int timer);
        public abstract void end(ServerPlayerEntity player);
        
        public boolean isComplete(int timer) {
            return timer >= duration;
        }
        
        /**
         * Approach phase - subtle hints of something wrong.
         */
        public static class ApproachPhase extends NightmarePhase {
            public ApproachPhase(FearType fear) {
                super(fear, 100);
            }
            
            @Override
            public void begin(ServerPlayerEntity player) {
                // Dim ambient sounds, slight fog
            }
            
            @Override
            public void tick(ServerPlayerEntity player, int timer) {
                if (timer % 40 == 0) {
                    sendSubtleMessage(player);
                }
            }
            
            @Override
            public void end(ServerPlayerEntity player) {}
            
            private void sendSubtleMessage(ServerPlayerEntity player) {
                String[] messages = {
                    "You feel watched...",
                    "Something is wrong...",
                    "The air grows cold...",
                    "Shadows seem to move..."
                };
                player.displayClientMessage(
                    new StringTextComponent(messages[random.nextInt(messages.length)])
                        .withStyle(TextFormatting.GRAY, TextFormatting.ITALIC),
                    true
                );
            }
        }
        
        /**
         * Buildup phase - fear intensifies.
         */
        public static class BuildupPhase extends NightmarePhase {
            public BuildupPhase(FearType fear) {
                super(fear, 200);
            }
            
            @Override
            public void begin(ServerPlayerEntity player) {
                player.displayClientMessage(
                    new StringTextComponent(fear.getDescription() + "...")
                        .withStyle(TextFormatting.RED),
                    true
                );
            }
            
            @Override
            public void tick(ServerPlayerEntity player, int timer) {
                // Apply effects based on fear type
                if (timer % 20 == 0) {
                    applyFearEffect(player);
                }
            }
            
            @Override
            public void end(ServerPlayerEntity player) {}
            
            private void applyFearEffect(ServerPlayerEntity player) {
                switch (fear) {
                    case DARKNESS:
                        // Apply blindness pulse
                        player.addEffect(new net.minecraft.potion.EffectInstance(
                            net.minecraft.potion.Effects.BLINDNESS, 40, 0));
                        break;
                    case FALLING:
                    case HEIGHTS:
                        // Apply levitation
                        player.addEffect(new net.minecraft.potion.EffectInstance(
                            net.minecraft.potion.Effects.LEVITATION, 20, 0));
                        break;
                    default:
                        break;
                }
            }
        }
        
        /**
         * Climax phase - peak terror.
         */
        public static class ClimaxPhase extends NightmarePhase {
            public ClimaxPhase(FearType fear) {
                super(fear, 100);
            }
            
            @Override
            public void begin(ServerPlayerEntity player) {
                player.displayClientMessage(
                    new StringTextComponent("IT'S HERE!")
                        .withStyle(TextFormatting.DARK_RED, TextFormatting.BOLD),
                    false
                );
            }
            
            @Override
            public void tick(ServerPlayerEntity player, int timer) {
                // Maximum fear effects
                if (timer == 50) {
                    // Damage from pure terror
                    player.hurt(net.minecraft.util.DamageSource.MAGIC, 2.0f);
                }
            }
            
            @Override
            public void end(ServerPlayerEntity player) {}
        }
        
        /**
         * Resolution phase - terror fades.
         */
        public static class ResolutionPhase extends NightmarePhase {
            public ResolutionPhase(FearType fear) {
                super(fear, 60);
            }
            
            @Override
            public void begin(ServerPlayerEntity player) {}
            
            @Override
            public void tick(ServerPlayerEntity player, int timer) {
                // Gradually remove effects
            }
            
            @Override
            public void end(ServerPlayerEntity player) {
                player.displayClientMessage(
                    new StringTextComponent("The nightmare fades...")
                        .withStyle(TextFormatting.GRAY),
                    true
                );
            }
        }
    }
    
    /**
     * Record of a past nightmare for analytics.
     */
    public static class NightmareRecord {
        public final FearType fear;
        public final long timestamp;
        public final double intensity;
        public final boolean survived;
        
        public NightmareRecord(FearType fear, double intensity, boolean survived) {
            this.fear = fear;
            this.timestamp = System.currentTimeMillis();
            this.intensity = intensity;
            this.survived = survived;
        }
    }
}
