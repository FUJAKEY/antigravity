package com.antigravity.mod.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.Constants;

import java.util.*;

/**
 * Corruption Spread Handler
 * Manages a spreading corruption mechanic that transforms blocks and affects entities.
 * Corruption spreads organically from source points and can be contained or cleansed.
 */
public class CorruptionSpreadHandler {
    
    private static final Map<String, CorruptionSpreadHandler> INSTANCES = new HashMap<>();
    
    private final World world;
    private final List<CorruptionSource> corruptionSources = new ArrayList<>();
    private final Set<BlockPos> corruptedBlocks = new HashSet<>();
    private final Map<BlockPos, Integer> corruptionLevels = new HashMap<>();
    private final Random random = new Random();
    
    // Configuration
    private static final int MAX_CORRUPTION_LEVEL = 100;
    private static final int SPREAD_INTERVAL = 40; // Ticks between spread attempts
    private static final double SPREAD_CHANCE = 0.3;
    private static final int MAX_SPREAD_DISTANCE = 64;
    
    // Block transformation map
    private static final Map<Block, Block> CORRUPTION_TRANSFORMS = new HashMap<>();
    
    static {
        CORRUPTION_TRANSFORMS.put(Blocks.GRASS_BLOCK, Blocks.PODZOL);
        CORRUPTION_TRANSFORMS.put(Blocks.DIRT, Blocks.COARSE_DIRT);
        CORRUPTION_TRANSFORMS.put(Blocks.STONE, Blocks.COBBLESTONE);
        CORRUPTION_TRANSFORMS.put(Blocks.OAK_LEAVES, Blocks.AIR);
        CORRUPTION_TRANSFORMS.put(Blocks.BIRCH_LEAVES, Blocks.AIR);
        CORRUPTION_TRANSFORMS.put(Blocks.OAK_LOG, Blocks.STRIPPED_OAK_LOG);
        CORRUPTION_TRANSFORMS.put(Blocks.WATER, Blocks.ICE);
        CORRUPTION_TRANSFORMS.put(Blocks.SAND, Blocks.SOUL_SAND);
    }
    
    public static CorruptionSpreadHandler get(ServerWorld world) {
        String key = world.dimension().location().toString();
        return INSTANCES.computeIfAbsent(key, k -> new CorruptionSpreadHandler(world));
    }
    
    private CorruptionSpreadHandler(World world) {
        this.world = world;
    }
    
    /**
     * Main tick method - handles corruption spreading logic.
     */
    public void tick() {
        if (world.getGameTime() % SPREAD_INTERVAL != 0) return;
        
        // Update all corruption sources
        for (CorruptionSource source : corruptionSources) {
            source.tick();
            spreadFromSource(source);
        }
        
        // Decay isolated corruption
        decayIsolatedCorruption();
        
        // Apply effects to players in corrupted areas
        applyCorruptionEffects();
    }
    
    /**
     * Spreads corruption from a source point.
     */
    private void spreadFromSource(CorruptionSource source) {
        if (!source.isActive()) return;
        
        BlockPos center = source.getPosition();
        int currentRadius = source.getCurrentRadius();
        
        // Find blocks at the edge of current corruption
        List<BlockPos> edgeBlocks = findCorruptionEdge(center, currentRadius);
        
        // Attempt to corrupt edge blocks
        for (BlockPos edge : edgeBlocks) {
            if (random.nextDouble() < SPREAD_CHANCE * source.getSpreadMultiplier()) {
                attemptCorrupt(edge, source);
            }
        }
        
        // Expand radius over time
        if (currentRadius < MAX_SPREAD_DISTANCE && 
            source.getAge() % 200 == 0) {
            source.expandRadius();
        }
    }
    
    /**
     * Finds blocks at the edge of corruption spread.
     */
    private List<BlockPos> findCorruptionEdge(BlockPos center, int radius) {
        List<BlockPos> edge = new ArrayList<>();
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius / 2; y <= radius / 2; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    
                    // Check if this is an edge block (corrupted block next to non-corrupted)
                    if (corruptedBlocks.contains(pos)) {
                        for (BlockPos neighbor : getNeighbors(pos)) {
                            if (!corruptedBlocks.contains(neighbor) && 
                                canCorrupt(neighbor)) {
                                edge.add(neighbor);
                            }
                        }
                    }
                }
            }
        }
        
        return edge;
    }
    
    private List<BlockPos> getNeighbors(BlockPos pos) {
        List<BlockPos> neighbors = new ArrayList<>();
        neighbors.add(pos.north());
        neighbors.add(pos.south());
        neighbors.add(pos.east());
        neighbors.add(pos.west());
        neighbors.add(pos.above());
        neighbors.add(pos.below());
        return neighbors;
    }
    
    /**
     * Attempts to corrupt a specific block.
     */
    private void attemptCorrupt(BlockPos pos, CorruptionSource source) {
        if (!canCorrupt(pos)) return;
        
        BlockState currentState = world.getBlockState(pos);
        Block corruptedVersion = CORRUPTION_TRANSFORMS.get(currentState.getBlock());
        
        if (corruptedVersion != null) {
            world.setBlock(pos, corruptedVersion.defaultBlockState(), 3);
        }
        
        corruptedBlocks.add(pos);
        corruptionLevels.put(pos, source.getCorruptionStrength());
    }
    
    /**
     * Checks if a block can be corrupted.
     */
    private boolean canCorrupt(BlockPos pos) {
        if (corruptedBlocks.contains(pos)) return false;
        
        BlockState state = world.getBlockState(pos);
        
        // Can't corrupt air or bedrock
        if (state.isAir() || state.getBlock() == Blocks.BEDROCK) return false;
        
        // Can't corrupt protected blocks
        if (isProtected(pos)) return false;
        
        return true;
    }
    
    /**
     * Checks if a position is protected from corruption (e.g., near a beacon).
     */
    private boolean isProtected(BlockPos pos) {
        // Check for nearby purifying blocks
        int searchRadius = 16;
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -searchRadius; y <= searchRadius; y++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    BlockPos check = pos.offset(x, y, z);
                    if (world.getBlockState(check).getBlock() == Blocks.BEACON) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * Decays corruption that is isolated from sources.
     */
    private void decayIsolatedCorruption() {
        List<BlockPos> toDecay = new ArrayList<>();
        
        for (BlockPos pos : corruptedBlocks) {
            boolean nearSource = false;
            for (CorruptionSource source : corruptionSources) {
                if (pos.closerThan(source.getPosition(), source.getCurrentRadius())) {
                    nearSource = true;
                    break;
                }
            }
            
            if (!nearSource) {
                int level = corruptionLevels.getOrDefault(pos, 0) - 1;
                if (level <= 0) {
                    toDecay.add(pos);
                } else {
                    corruptionLevels.put(pos, level);
                }
            }
        }
        
        for (BlockPos pos : toDecay) {
            cleanse(pos);
        }
    }
    
    /**
     * Cleanses corruption from a block.
     */
    public void cleanse(BlockPos pos) {
        corruptedBlocks.remove(pos);
        corruptionLevels.remove(pos);
        // Note: We don't revert block state - that would require tracking original state
    }
    
    /**
     * Applies effects to players in corrupted areas.
     */
    private void applyCorruptionEffects() {
        for (PlayerEntity player : world.players()) {
            BlockPos playerPos = player.blockPosition();
            int corruptionNearby = countNearbyCorruption(playerPos, 5);
            
            if (corruptionNearby > 10) {
                applyCorruptionDebuffs(player, corruptionNearby);
            }
        }
    }
    
    private int countNearbyCorruption(BlockPos center, int radius) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            if (corruptedBlocks.contains(pos)) count++;
        }
        return count;
    }
    
    private void applyCorruptionDebuffs(PlayerEntity player, int intensity) {
        // Apply nausea and slowness based on corruption intensity
        // In a real implementation, would apply potion effects here
    }
    
    /**
     * Creates a new corruption source at the given position.
     */
    public void createSource(BlockPos pos, CorruptionType type, int strength) {
        CorruptionSource source = new CorruptionSource(pos, type, strength);
        corruptionSources.add(source);
        
        // Immediately corrupt the source block
        corruptedBlocks.add(pos);
        corruptionLevels.put(pos, strength);
    }
    
    /**
     * Removes a corruption source.
     */
    public void removeSource(BlockPos pos) {
        corruptionSources.removeIf(s -> s.getPosition().equals(pos));
    }
    
    public boolean isCorrupted(BlockPos pos) {
        return corruptedBlocks.contains(pos);
    }
    
    public int getCorruptionLevel(BlockPos pos) {
        return corruptionLevels.getOrDefault(pos, 0);
    }
    
    public CompoundNBT save() {
        CompoundNBT nbt = new CompoundNBT();
        
        ListNBT sourcesList = new ListNBT();
        for (CorruptionSource source : corruptionSources) {
            sourcesList.add(source.save());
        }
        nbt.put("Sources", sourcesList);
        
        ListNBT blocksList = new ListNBT();
        for (BlockPos pos : corruptedBlocks) {
            CompoundNBT blockNbt = new CompoundNBT();
            blockNbt.putInt("X", pos.getX());
            blockNbt.putInt("Y", pos.getY());
            blockNbt.putInt("Z", pos.getZ());
            blockNbt.putInt("Level", corruptionLevels.getOrDefault(pos, 0));
            blocksList.add(blockNbt);
        }
        nbt.put("Blocks", blocksList);
        
        return nbt;
    }
    
    public void load(CompoundNBT nbt) {
        corruptionSources.clear();
        corruptedBlocks.clear();
        corruptionLevels.clear();
        
        ListNBT sourcesList = nbt.getList("Sources", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < sourcesList.size(); i++) {
            corruptionSources.add(CorruptionSource.load(sourcesList.getCompound(i)));
        }
        
        ListNBT blocksList = nbt.getList("Blocks", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < blocksList.size(); i++) {
            CompoundNBT blockNbt = blocksList.getCompound(i);
            BlockPos pos = new BlockPos(blockNbt.getInt("X"), blockNbt.getInt("Y"), blockNbt.getInt("Z"));
            corruptedBlocks.add(pos);
            corruptionLevels.put(pos, blockNbt.getInt("Level"));
        }
    }
    
    /**
     * Represents a source point of corruption.
     */
    public static class CorruptionSource {
        private final BlockPos position;
        private final CorruptionType type;
        private final int corruptionStrength;
        private int age = 0;
        private int currentRadius = 3;
        private boolean active = true;
        
        public CorruptionSource(BlockPos pos, CorruptionType type, int strength) {
            this.position = pos;
            this.type = type;
            this.corruptionStrength = strength;
        }
        
        public void tick() {
            age++;
        }
        
        public void expandRadius() {
            currentRadius = Math.min(currentRadius + 1, MAX_SPREAD_DISTANCE);
        }
        
        public double getSpreadMultiplier() {
            return type.getSpreadMultiplier();
        }
        
        public BlockPos getPosition() { return position; }
        public CorruptionType getType() { return type; }
        public int getCorruptionStrength() { return corruptionStrength; }
        public int getCurrentRadius() { return currentRadius; }
        public int getAge() { return age; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        
        public CompoundNBT save() {
            CompoundNBT nbt = new CompoundNBT();
            nbt.putInt("X", position.getX());
            nbt.putInt("Y", position.getY());
            nbt.putInt("Z", position.getZ());
            nbt.putString("Type", type.name());
            nbt.putInt("Strength", corruptionStrength);
            nbt.putInt("Age", age);
            nbt.putInt("Radius", currentRadius);
            nbt.putBoolean("Active", active);
            return nbt;
        }
        
        public static CorruptionSource load(CompoundNBT nbt) {
            BlockPos pos = new BlockPos(nbt.getInt("X"), nbt.getInt("Y"), nbt.getInt("Z"));
            CorruptionType type = CorruptionType.valueOf(nbt.getString("Type"));
            CorruptionSource source = new CorruptionSource(pos, type, nbt.getInt("Strength"));
            source.age = nbt.getInt("Age");
            source.currentRadius = nbt.getInt("Radius");
            source.active = nbt.getBoolean("Active");
            return source;
        }
    }
    
    /**
     * Types of corruption with different spread behaviors.
     */
    public enum CorruptionType {
        SHADOW(1.0, true, false),
        VOID(1.5, true, true),
        NECROTIC(0.8, false, true),
        ELDRITCH(2.0, true, true);
        
        private final double spreadMultiplier;
        private final boolean affectsPlayers;
        private final boolean affectsEntities;
        
        CorruptionType(double spread, boolean players, boolean entities) {
            this.spreadMultiplier = spread;
            this.affectsPlayers = players;
            this.affectsEntities = entities;
        }
        
        public double getSpreadMultiplier() { return spreadMultiplier; }
        public boolean affectsPlayers() { return affectsPlayers; }
        public boolean affectsEntities() { return affectsEntities; }
    }
}
