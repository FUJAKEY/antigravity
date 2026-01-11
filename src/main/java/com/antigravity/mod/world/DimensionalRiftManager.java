package com.antigravity.mod.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.Constants;

import java.util.*;

/**
 * Dimensional Rift Manager
 * Manages the creation, evolution, and effects of dimensional rifts in the world.
 * Rifts are unstable tears in reality that can spawn entities, warp physics, and spread corruption.
 */
public class DimensionalRiftManager {
    
    private static final Map<String, DimensionalRiftManager> INSTANCES = new HashMap<>();
    private final List<DimensionalRift> activeRifts = new ArrayList<>();
    private final Random random = new Random();
    private final World world;
    
    // Configuration
    private static final int MAX_RIFTS_PER_WORLD = 10;
    private static final int RIFT_SPAWN_CHANCE = 1000; // 1 in X per tick
    private static final double RIFT_EFFECT_RADIUS = 16.0;
    
    public static DimensionalRiftManager get(ServerWorld world) {
        String key = world.dimension().location().toString();
        return INSTANCES.computeIfAbsent(key, k -> new DimensionalRiftManager(world));
    }
    
    private DimensionalRiftManager(World world) {
        this.world = world;
    }
    
    /**
     * Main tick loop - called every server tick.
     * Handles rift evolution, effect application, and cleanup.
     */
    public void tick() {
        // Spawn new rifts randomly
        if (activeRifts.size() < MAX_RIFTS_PER_WORLD && random.nextInt(RIFT_SPAWN_CHANCE) == 0) {
            attemptSpawnRift();
        }
        
        // Update all existing rifts
        Iterator<DimensionalRift> iterator = activeRifts.iterator();
        while (iterator.hasNext()) {
            DimensionalRift rift = iterator.next();
            rift.tick();
            
            if (rift.isExpired()) {
                rift.onClose();
                iterator.remove();
            } else {
                applyRiftEffects(rift);
            }
        }
    }
    
    /**
     * Attempts to spawn a new rift near a random player.
     */
    private void attemptSpawnRift() {
        List<? extends PlayerEntity> players = world.players();
        if (players.isEmpty()) return;
        
        PlayerEntity target = players.get(random.nextInt(players.size()));
        BlockPos playerPos = target.blockPosition();
        
        // Find a valid spawn position
        int offsetX = random.nextInt(64) - 32;
        int offsetZ = random.nextInt(64) - 32;
        BlockPos spawnPos = new BlockPos(playerPos.getX() + offsetX, playerPos.getY(), playerPos.getZ() + offsetZ);
        
        // Find ground level
        spawnPos = findGroundLevel(spawnPos);
        
        if (spawnPos != null && isValidRiftLocation(spawnPos)) {
            RiftType type = RiftType.values()[random.nextInt(RiftType.values().length)];
            DimensionalRift rift = new DimensionalRift(spawnPos, type, random.nextInt(6000) + 1200);
            activeRifts.add(rift);
        }
    }
    
    private BlockPos findGroundLevel(BlockPos pos) {
        for (int y = pos.getY(); y > 0; y--) {
            BlockPos check = new BlockPos(pos.getX(), y, pos.getZ());
            if (world.getBlockState(check).getMaterial().isSolid() && 
                world.getBlockState(check.above()).isAir()) {
                return check.above();
            }
        }
        return null;
    }
    
    private boolean isValidRiftLocation(BlockPos pos) {
        // Check if there's enough space
        for (int dy = 0; dy < 3; dy++) {
            if (!world.getBlockState(pos.above(dy)).isAir()) return false;
        }
        return true;
    }
    
    /**
     * Applies the effects of a rift to nearby entities and blocks.
     */
    private void applyRiftEffects(DimensionalRift rift) {
        BlockPos center = rift.getPosition();
        double radius = rift.getEffectRadius();
        
        // Affect players
        for (PlayerEntity player : world.players()) {
            double distance = player.distanceToSqr(center.getX(), center.getY(), center.getZ());
            if (distance < radius * radius) {
                rift.getType().applyEffect(player, 1.0 - (Math.sqrt(distance) / radius));
            }
        }
        
        // Spawn particles on client side
        if (world instanceof ServerWorld) {
            spawnRiftParticles((ServerWorld) world, center, rift);
        }
    }
    
    private void spawnRiftParticles(ServerWorld world, BlockPos pos, DimensionalRift rift) {
        for (int i = 0; i < 5; i++) {
            double x = pos.getX() + 0.5 + random.nextGaussian() * 0.5;
            double y = pos.getY() + 1.0 + random.nextGaussian() * 0.5;
            double z = pos.getZ() + 0.5 + random.nextGaussian() * 0.5;
            world.sendParticles(ParticleTypes.PORTAL, x, y, z, 1, 0, 0, 0, 0.1);
        }
    }
    
    public void forceSpawnRift(BlockPos pos, RiftType type, int duration) {
        activeRifts.add(new DimensionalRift(pos, type, duration));
    }
    
    public List<DimensionalRift> getRiftsNear(BlockPos pos, double radius) {
        List<DimensionalRift> nearby = new ArrayList<>();
        for (DimensionalRift rift : activeRifts) {
            if (rift.getPosition().closerThan(pos, radius)) {
                nearby.add(rift);
            }
        }
        return nearby;
    }
    
    public CompoundNBT save() {
        CompoundNBT nbt = new CompoundNBT();
        ListNBT riftList = new ListNBT();
        for (DimensionalRift rift : activeRifts) {
            riftList.add(rift.save());
        }
        nbt.put("Rifts", riftList);
        return nbt;
    }
    
    public void load(CompoundNBT nbt) {
        activeRifts.clear();
        ListNBT riftList = nbt.getList("Rifts", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < riftList.size(); i++) {
            activeRifts.add(DimensionalRift.load(riftList.getCompound(i)));
        }
    }
    
    /**
     * Represents a single dimensional rift in the world.
     */
    public static class DimensionalRift {
        private final BlockPos position;
        private final RiftType type;
        private int lifetime;
        private int age = 0;
        private double stability = 1.0;
        private final RiftEvolutionEngine evolution;
        
        public DimensionalRift(BlockPos pos, RiftType type, int lifetime) {
            this.position = pos;
            this.type = type;
            this.lifetime = lifetime;
            this.evolution = new RiftEvolutionEngine(this);
        }
        
        public void tick() {
            age++;
            evolution.tick();
            
            // Stability decreases over time
            stability = Math.max(0.0, 1.0 - (double) age / lifetime);
            
            // Unstable rifts grow more powerful
            if (stability < 0.3 && age % 20 == 0) {
                type.onUnstable(this);
            }
        }
        
        public boolean isExpired() {
            return age >= lifetime || stability <= 0;
        }
        
        public void onClose() {
            // Cleanup effects when rift closes
        }
        
        public BlockPos getPosition() { return position; }
        public RiftType getType() { return type; }
        public double getStability() { return stability; }
        public int getAge() { return age; }
        
        public double getEffectRadius() {
            return RIFT_EFFECT_RADIUS * (1.0 + (1.0 - stability) * 0.5);
        }
        
        public CompoundNBT save() {
            CompoundNBT nbt = new CompoundNBT();
            nbt.putInt("X", position.getX());
            nbt.putInt("Y", position.getY());
            nbt.putInt("Z", position.getZ());
            nbt.putString("Type", type.name());
            nbt.putInt("Lifetime", lifetime);
            nbt.putInt("Age", age);
            nbt.putDouble("Stability", stability);
            return nbt;
        }
        
        public static DimensionalRift load(CompoundNBT nbt) {
            BlockPos pos = new BlockPos(nbt.getInt("X"), nbt.getInt("Y"), nbt.getInt("Z"));
            RiftType type = RiftType.valueOf(nbt.getString("Type"));
            DimensionalRift rift = new DimensionalRift(pos, type, nbt.getInt("Lifetime"));
            rift.age = nbt.getInt("Age");
            rift.stability = nbt.getDouble("Stability");
            return rift;
        }
    }
    
    /**
     * Defines different types of dimensional rifts and their effects.
     */
    public enum RiftType {
        VOID {
            @Override
            public void applyEffect(PlayerEntity player, double intensity) {
                // Void rifts drain hunger
                if (intensity > 0.5) {
                    player.getFoodData().addExhaustion((float) (0.1 * intensity));
                }
            }
        },
        NETHER {
            @Override
            public void applyEffect(PlayerEntity player, double intensity) {
                // Nether rifts cause fire damage at close range
                if (intensity > 0.8) {
                    player.setSecondsOnFire(1);
                }
            }
        },
        END {
            @Override
            public void applyEffect(PlayerEntity player, double intensity) {
                // End rifts cause levitation
                if (intensity > 0.6) {
                    // Would apply levitation effect here
                }
            }
        },
        TEMPORAL {
            @Override
            public void applyEffect(PlayerEntity player, double intensity) {
                // Temporal rifts slow movement
                // Would apply slowness effect here
            }
        },
        SHADOW {
            @Override
            public void applyEffect(PlayerEntity player, double intensity) {
                // Shadow rifts cause blindness pulses
                // Would apply blindness here
            }
        };
        
        public abstract void applyEffect(PlayerEntity player, double intensity);
        
        public void onUnstable(DimensionalRift rift) {
            // Called when rift becomes unstable
        }
    }
    
    /**
     * Handles the evolution of a rift over its lifetime.
     * Rifts can grow, shrink, change type, or spawn phenomena.
     */
    public static class RiftEvolutionEngine {
        private final DimensionalRift rift;
        private double growthFactor = 1.0;
        private int phenomenaCounter = 0;
        private final List<RiftPhenomenon> activePhenomena = new ArrayList<>();
        
        public RiftEvolutionEngine(DimensionalRift rift) {
            this.rift = rift;
        }
        
        public void tick() {
            phenomenaCounter++;
            
            // Every 100 ticks, evaluate state
            if (phenomenaCounter % 100 == 0) {
                evaluateEvolution();
            }
            
            // Tick active phenomena
            activePhenomena.removeIf(p -> !p.tick());
        }
        
        private void evaluateEvolution() {
            double stability = rift.getStability();
            
            if (stability < 0.5 && new Random().nextDouble() < 0.3) {
                spawnPhenomenon();
            }
            
            // Calculate growth factor based on age
            growthFactor = 1.0 + (rift.getAge() / 1000.0);
        }
        
        private void spawnPhenomenon() {
            RiftPhenomenon phenomenon = new RiftPhenomenon(rift.getPosition(), rift.getType());
            activePhenomena.add(phenomenon);
        }
        
        public double getGrowthFactor() { return growthFactor; }
    }
    
    /**
     * Represents a temporary phenomenon spawned by a rift.
     */
    public static class RiftPhenomenon {
        private final BlockPos origin;
        private final RiftType sourceType;
        private int duration;
        private final int maxDuration;
        
        public RiftPhenomenon(BlockPos origin, RiftType type) {
            this.origin = origin;
            this.sourceType = type;
            this.maxDuration = 200;
            this.duration = maxDuration;
        }
        
        public boolean tick() {
            duration--;
            
            // Apply phenomenon specific effects
            applyEffect();
            
            return duration > 0;
        }
        
        private void applyEffect() {
            // Each phenomenon type has different effects
            switch (sourceType) {
                case VOID:
                    // Void phenomena darken the area
                    break;
                case NETHER:
                    // Nether phenomena spawn fire
                    break;
                case END:
                    // End phenomena create floating blocks
                    break;
                case TEMPORAL:
                    // Temporal phenomena create time echoes
                    break;
                case SHADOW:
                    // Shadow phenomena spawn shadow entities
                    break;
            }
        }
    }
}
