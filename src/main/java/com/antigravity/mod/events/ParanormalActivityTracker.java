package com.antigravity.mod.events;

import com.antigravity.mod.AntigravityMod;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * Paranormal Activity Tracker
 * Tracks and escalates paranormal activity levels in the world.
 * Activity builds up over time and triggers increasingly severe events.
 */
@Mod.EventBusSubscriber(modid = AntigravityMod.MOD_ID)
public class ParanormalActivityTracker {
    
    private static final Map<String, WorldActivityData> worldData = new HashMap<>();
    private static final Random random = new Random();
    
    // Global configuration
    private static final int ACTIVITY_DECAY_RATE = 1;
    private static final int MAX_ACTIVITY_LEVEL = 1000;
    private static final int EVENT_CHECK_INTERVAL = 200;
    
    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.world instanceof ServerWorld)) return;
        
        ServerWorld world = (ServerWorld) event.world;
        WorldActivityData data = getOrCreateData(world);
        
        data.tick(world);
    }
    
    private static WorldActivityData getOrCreateData(ServerWorld world) {
        String key = world.dimension().location().toString();
        return worldData.computeIfAbsent(key, k -> new WorldActivityData());
    }
    
    /**
     * Reports a paranormal event, increasing activity level.
     */
    public static void reportActivity(World world, BlockPos location, ActivityType type, int intensity) {
        String key = world.dimension().location().toString();
        WorldActivityData data = worldData.get(key);
        
        if (data != null) {
            data.recordActivity(location, type, intensity);
        }
    }
    
    /**
     * Gets the current activity level for a world.
     */
    public static int getActivityLevel(World world) {
        String key = world.dimension().location().toString();
        WorldActivityData data = worldData.get(key);
        return data != null ? data.getTotalActivityLevel() : 0;
    }
    
    /**
     * Gets the activity phase for a world.
     */
    public static ActivityPhase getActivityPhase(World world) {
        int level = getActivityLevel(world);
        
        if (level >= 800) return ActivityPhase.CRITICAL;
        if (level >= 500) return ActivityPhase.HIGH;
        if (level >= 200) return ActivityPhase.MODERATE;
        if (level >= 50) return ActivityPhase.LOW;
        return ActivityPhase.DORMANT;
    }
    
    /**
     * Data tracker for a specific world's paranormal activity.
     */
    public static class WorldActivityData {
        private int totalActivityLevel = 0;
        private int tickCounter = 0;
        private final Map<BlockPos, RegionActivity> regionActivities = new HashMap<>();
        private final List<ActivityRecord> recentActivity = new ArrayList<>();
        private final EventEscalationManager escalationManager = new EventEscalationManager();
        private final ActivityPatternAnalyzer patternAnalyzer = new ActivityPatternAnalyzer();
        
        public void tick(ServerWorld world) {
            tickCounter++;
            
            // Decay activity over time
            if (tickCounter % 100 == 0) {
                decayActivity();
            }
            
            // Check for event triggering
            if (tickCounter % EVENT_CHECK_INTERVAL == 0) {
                escalationManager.evaluate(world, this);
            }
            
            // Analyze patterns
            if (tickCounter % 500 == 0) {
                patternAnalyzer.analyze(this);
            }
            
            // Tick region activities
            for (RegionActivity region : regionActivities.values()) {
                region.tick();
            }
        }
        
        public void recordActivity(BlockPos location, ActivityType type, int intensity) {
            // Update total
            totalActivityLevel = Math.min(MAX_ACTIVITY_LEVEL, totalActivityLevel + intensity);
            
            // Record in region
            BlockPos regionKey = new BlockPos(
                location.getX() / 64 * 64,
                location.getY() / 64 * 64,
                location.getZ() / 64 * 64
            );
            
            RegionActivity region = regionActivities.computeIfAbsent(regionKey, 
                k -> new RegionActivity(regionKey));
            region.addActivity(type, intensity);
            
            // Add to recent activity
            recentActivity.add(new ActivityRecord(type, location, intensity, System.currentTimeMillis()));
            
            // Limit history size
            while (recentActivity.size() > 100) {
                recentActivity.remove(0);
            }
        }
        
        private void decayActivity() {
            totalActivityLevel = Math.max(0, totalActivityLevel - ACTIVITY_DECAY_RATE);
            
            // Decay regions
            Iterator<Map.Entry<BlockPos, RegionActivity>> iterator = regionActivities.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<BlockPos, RegionActivity> entry = iterator.next();
                entry.getValue().decay();
                if (entry.getValue().getLevel() <= 0) {
                    iterator.remove();
                }
            }
        }
        
        public int getTotalActivityLevel() { return totalActivityLevel; }
        public List<ActivityRecord> getRecentActivity() { return Collections.unmodifiableList(recentActivity); }
        public Map<BlockPos, RegionActivity> getRegionActivities() { return regionActivities; }
    }
    
    /**
     * Types of paranormal activity.
     */
    public enum ActivityType {
        ENTITY_SPAWN(10, "Entity Manifestation"),
        BLOCK_CORRUPTION(5, "Block Corruption"),
        PLAYER_DEATH(20, "Player Death"),
        RITUAL_PERFORMED(30, "Ritual Performed"),
        RIFT_OPENED(25, "Dimensional Rift"),
        SANITY_BREAK(15, "Sanity Break"),
        BLOOD_MOON(50, "Blood Moon"),
        UNKNOWN(5, "Unknown Disturbance");
        
        private final int baseIntensity;
        private final String displayName;
        
        ActivityType(int intensity, String name) {
            this.baseIntensity = intensity;
            this.displayName = name;
        }
        
        public int getBaseIntensity() { return baseIntensity; }
        public String getDisplayName() { return displayName; }
    }
    
    /**
     * Phases of paranormal activity.
     */
    public enum ActivityPhase {
        DORMANT("All is quiet", TextFormatting.GREEN),
        LOW("Something stirs", TextFormatting.YELLOW),
        MODERATE("The veil weakens", TextFormatting.GOLD),
        HIGH("Dark forces gather", TextFormatting.RED),
        CRITICAL("Reality fractures", TextFormatting.DARK_RED);
        
        private final String description;
        private final TextFormatting color;
        
        ActivityPhase(String desc, TextFormatting color) {
            this.description = desc;
            this.color = color;
        }
        
        public String getDescription() { return description; }
        public TextFormatting getColor() { return color; }
    }
    
    /**
     * Tracks activity in a specific region.
     */
    public static class RegionActivity {
        private final BlockPos center;
        private int level = 0;
        private final Map<ActivityType, Integer> typeBreakdown = new EnumMap<>(ActivityType.class);
        private boolean hotspot = false;
        
        public RegionActivity(BlockPos center) {
            this.center = center;
        }
        
        public void addActivity(ActivityType type, int intensity) {
            level += intensity;
            typeBreakdown.merge(type, intensity, Integer::sum);
            
            // Check for hotspot threshold
            if (level > 100) {
                hotspot = true;
            }
        }
        
        public void tick() {
            // Hotspots generate ambient effects
        }
        
        public void decay() {
            level = Math.max(0, level - 1);
            if (level < 50) {
                hotspot = false;
            }
        }
        
        public int getLevel() { return level; }
        public boolean isHotspot() { return hotspot; }
        public BlockPos getCenter() { return center; }
        public ActivityType getDominantType() {
            return typeBreakdown.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(ActivityType.UNKNOWN);
        }
    }
    
    /**
     * Record of a single paranormal activity event.
     */
    public static class ActivityRecord {
        public final ActivityType type;
        public final BlockPos location;
        public final int intensity;
        public final long timestamp;
        
        public ActivityRecord(ActivityType type, BlockPos location, int intensity, long timestamp) {
            this.type = type;
            this.location = location;
            this.intensity = intensity;
            this.timestamp = timestamp;
        }
    }
    
    /**
     * Manages the escalation of paranormal events based on activity levels.
     */
    public static class EventEscalationManager {
        private int escalationLevel = 0;
        private long lastMajorEvent = 0;
        
        public void evaluate(ServerWorld world, WorldActivityData data) {
            ActivityPhase phase = getPhaseFromLevel(data.getTotalActivityLevel());
            
            // Higher phases trigger more severe events
            switch (phase) {
                case CRITICAL:
                    if (random.nextDouble() < 0.3) triggerCriticalEvent(world, data);
                    break;
                case HIGH:
                    if (random.nextDouble() < 0.2) triggerHighEvent(world, data);
                    break;
                case MODERATE:
                    if (random.nextDouble() < 0.1) triggerModerateEvent(world, data);
                    break;
                case LOW:
                    if (random.nextDouble() < 0.05) triggerLowEvent(world, data);
                    break;
                default:
                    break;
            }
        }
        
        private ActivityPhase getPhaseFromLevel(int level) {
            if (level >= 800) return ActivityPhase.CRITICAL;
            if (level >= 500) return ActivityPhase.HIGH;
            if (level >= 200) return ActivityPhase.MODERATE;
            if (level >= 50) return ActivityPhase.LOW;
            return ActivityPhase.DORMANT;
        }
        
        private void triggerCriticalEvent(ServerWorld world, WorldActivityData data) {
            // Notify all players
            for (ServerPlayerEntity player : world.players()) {
                player.displayClientMessage(
                    new StringTextComponent("Reality itself shudders...")
                        .withStyle(TextFormatting.DARK_RED, TextFormatting.BOLD),
                    false
                );
            }
            
            // Spawn multiple rifts, trigger blood moon, etc.
            escalationLevel++;
        }
        
        private void triggerHighEvent(ServerWorld world, WorldActivityData data) {
            for (ServerPlayerEntity player : world.players()) {
                player.displayClientMessage(
                    new StringTextComponent("Dark whispers fill your mind...")
                        .withStyle(TextFormatting.RED),
                    true
                );
            }
        }
        
        private void triggerModerateEvent(ServerWorld world, WorldActivityData data) {
            // Random player gets a scare
            List<ServerPlayerEntity> players = world.players();
            if (!players.isEmpty()) {
                ServerPlayerEntity target = players.get(random.nextInt(players.size()));
                target.displayClientMessage(
                    new StringTextComponent("Something is watching...")
                        .withStyle(TextFormatting.GRAY),
                    true
                );
            }
        }
        
        private void triggerLowEvent(ServerWorld world, WorldActivityData data) {
            // Ambient effects only
        }
        
        public int getEscalationLevel() { return escalationLevel; }
    }
    
    /**
     * Analyzes patterns in paranormal activity.
     */
    public static class ActivityPatternAnalyzer {
        private final Map<ActivityType, Integer> typeTotals = new EnumMap<>(ActivityType.class);
        private double activityTrend = 0.0;
        private int previousTotal = 0;
        
        public void analyze(WorldActivityData data) {
            // Calculate activity trend
            int currentTotal = data.getTotalActivityLevel();
            activityTrend = (currentTotal - previousTotal) / 100.0;
            previousTotal = currentTotal;
            
            // Aggregate type totals from recent activity
            typeTotals.clear();
            for (ActivityRecord record : data.getRecentActivity()) {
                typeTotals.merge(record.type, record.intensity, Integer::sum);
            }
        }
        
        public ActivityType getPredominantActivity() {
            return typeTotals.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(ActivityType.UNKNOWN);
        }
        
        public double getActivityTrend() { return activityTrend; }
        
        public boolean isRising() { return activityTrend > 0.1; }
        public boolean isFalling() { return activityTrend < -0.1; }
        public boolean isStable() { return Math.abs(activityTrend) <= 0.1; }
    }
}
