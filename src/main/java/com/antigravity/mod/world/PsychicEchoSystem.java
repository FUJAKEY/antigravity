package com.antigravity.mod.world;

import com.antigravity.mod.AntigravityMod;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.Constants;

import java.util.*;

/**
 * Psychic Echo System
 * Records player actions and replays them as ghostly "echoes" visible to other players.
 * Creates an eerie sense of past presence in areas.
 */
public class PsychicEchoSystem {
    
    private static final Map<String, PsychicEchoSystem> INSTANCES = new HashMap<>();
    private final List<EchoRecording> recordings = new ArrayList<>();
    private final List<ActiveEcho> activeEchoes = new ArrayList<>();
    private final Random random = new Random();
    
    // Configuration
    private static final int MAX_RECORDINGS = 100;
    private static final int MAX_RECORDING_LENGTH = 600; // 30 seconds
    private static final double ECHO_TRIGGER_RADIUS = 32.0;
    private static final double ECHO_SPAWN_CHANCE = 0.01;
    
    public static PsychicEchoSystem get(ServerWorld world) {
        String key = world.dimension().location().toString();
        return INSTANCES.computeIfAbsent(key, k -> new PsychicEchoSystem());
    }
    
    /**
     * Records a player's current state for potential echo playback.
     */
    public void recordPlayer(ServerPlayerEntity player) {
        // Find or create recording for this player
        EchoRecording recording = findActiveRecording(player.getUUID());
        
        if (recording == null) {
            recording = new EchoRecording(player.getUUID(), player.getName().getString());
            recordings.add(recording);
            
            // Limit total recordings
            while (recordings.size() > MAX_RECORDINGS) {
                recordings.remove(0);
            }
        }
        
        // Record current frame
        recording.recordFrame(player);
    }
    
    /**
     * Finalizes a recording when player leaves area or dies.
     */
    public void finalizeRecording(UUID playerId) {
        EchoRecording recording = findActiveRecording(playerId);
        if (recording != null) {
            recording.finalize();
        }
    }
    
    private EchoRecording findActiveRecording(UUID playerId) {
        for (EchoRecording recording : recordings) {
            if (recording.getPlayerId().equals(playerId) && !recording.isFinalized()) {
                return recording;
            }
        }
        return null;
    }
    
    /**
     * Main tick - handles echo triggering and playback.
     */
    public void tick(ServerWorld world) {
        // Check for echo triggering
        for (ServerPlayerEntity player : world.players()) {
            checkEchoTrigger(world, player);
        }
        
        // Tick active echoes
        Iterator<ActiveEcho> iterator = activeEchoes.iterator();
        while (iterator.hasNext()) {
            ActiveEcho echo = iterator.next();
            echo.tick(world);
            
            if (echo.isComplete()) {
                iterator.remove();
            }
        }
    }
    
    /**
     * Checks if an echo should be triggered near a player.
     */
    private void checkEchoTrigger(ServerWorld world, ServerPlayerEntity player) {
        if (random.nextDouble() > ECHO_SPAWN_CHANCE) return;
        
        // Find a recording near this player
        for (EchoRecording recording : recordings) {
            if (!recording.isFinalized()) continue;
            if (recording.getPlayerId().equals(player.getUUID())) continue; // Don't show own echoes
            
            if (recording.isNear(player.blockPosition(), ECHO_TRIGGER_RADIUS)) {
                // Trigger this echo
                triggerEcho(world, player, recording);
                return;
            }
        }
    }
    
    private void triggerEcho(ServerWorld world, ServerPlayerEntity viewer, EchoRecording recording) {
        // Check if this echo is already playing
        for (ActiveEcho echo : activeEchoes) {
            if (echo.getRecording() == recording) return;
        }
        
        ActiveEcho echo = new ActiveEcho(recording, viewer);
        activeEchoes.add(echo);
        
        viewer.displayClientMessage(
            new StringTextComponent("You sense a presence from the past...")
                .withStyle(TextFormatting.GRAY, TextFormatting.ITALIC),
            true
        );
    }
    
    /**
     * Records a death event for dramatic echo playback.
     */
    public void recordDeath(ServerPlayerEntity player, String deathMessage) {
        EchoRecording recording = findActiveRecording(player.getUUID());
        if (recording != null) {
            recording.setDeathEvent(deathMessage);
            recording.finalize();
        }
    }
    
    public CompoundNBT save() {
        CompoundNBT nbt = new CompoundNBT();
        ListNBT recordingList = new ListNBT();
        
        for (EchoRecording recording : recordings) {
            if (recording.isFinalized()) {
                recordingList.add(recording.save());
            }
        }
        
        nbt.put("Recordings", recordingList);
        return nbt;
    }
    
    public void load(CompoundNBT nbt) {
        recordings.clear();
        ListNBT recordingList = nbt.getList("Recordings", Constants.NBT.TAG_COMPOUND);
        
        for (int i = 0; i < recordingList.size(); i++) {
            recordings.add(EchoRecording.load(recordingList.getCompound(i)));
        }
    }
    
    /**
     * A recording of a player's actions over time.
     */
    public static class EchoRecording {
        private final UUID playerId;
        private final String playerName;
        private final List<EchoFrame> frames = new ArrayList<>();
        private final long recordingStart;
        private boolean finalized = false;
        private String deathEvent = null;
        private final EchoMetadata metadata;
        
        public EchoRecording(UUID id, String name) {
            this.playerId = id;
            this.playerName = name;
            this.recordingStart = System.currentTimeMillis();
            this.metadata = new EchoMetadata();
        }
        
        public void recordFrame(PlayerEntity player) {
            if (finalized) return;
            if (frames.size() >= MAX_RECORDING_LENGTH) {
                finalize();
                return;
            }
            
            EchoFrame frame = new EchoFrame(
                player.position(),
                player.yRot,
                player.xRot,
                player.isSprinting(),
                player.isCrouching(),
                player.getMainHandItem().isEmpty() ? "empty" : player.getMainHandItem().getItem().getRegistryName().toString()
            );
            
            frames.add(frame);
            metadata.update(player);
        }
        
        public void finalize() {
            finalized = true;
            metadata.finalize();
        }
        
        public boolean isNear(BlockPos pos, double radius) {
            if (frames.isEmpty()) return false;
            
            EchoFrame first = frames.get(0);
            return pos.closerThan(new BlockPos(first.position), radius);
        }
        
        public void setDeathEvent(String message) {
            this.deathEvent = message;
        }
        
        public UUID getPlayerId() { return playerId; }
        public String getPlayerName() { return playerName; }
        public List<EchoFrame> getFrames() { return Collections.unmodifiableList(frames); }
        public boolean isFinalized() { return finalized; }
        public String getDeathEvent() { return deathEvent; }
        public boolean hasDeath() { return deathEvent != null; }
        
        public CompoundNBT save() {
            CompoundNBT nbt = new CompoundNBT();
            nbt.putUUID("PlayerId", playerId);
            nbt.putString("PlayerName", playerName);
            nbt.putLong("Start", recordingStart);
            nbt.putBoolean("HasDeath", hasDeath());
            if (hasDeath()) {
                nbt.putString("Death", deathEvent);
            }
            
            ListNBT frameList = new ListNBT();
            for (EchoFrame frame : frames) {
                frameList.add(frame.save());
            }
            nbt.put("Frames", frameList);
            
            return nbt;
        }
        
        public static EchoRecording load(CompoundNBT nbt) {
            UUID id = nbt.getUUID("PlayerId");
            String name = nbt.getString("PlayerName");
            EchoRecording recording = new EchoRecording(id, name);
            
            if (nbt.getBoolean("HasDeath")) {
                recording.deathEvent = nbt.getString("Death");
            }
            
            ListNBT frameList = nbt.getList("Frames", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < frameList.size(); i++) {
                recording.frames.add(EchoFrame.load(frameList.getCompound(i)));
            }
            
            recording.finalized = true;
            return recording;
        }
    }
    
    /**
     * A single frame of echo recording.
     */
    public static class EchoFrame {
        public final Vector3d position;
        public final float yaw;
        public final float pitch;
        public final boolean sprinting;
        public final boolean sneaking;
        public final String heldItem;
        
        public EchoFrame(Vector3d pos, float yaw, float pitch, boolean sprint, boolean sneak, String item) {
            this.position = pos;
            this.yaw = yaw;
            this.pitch = pitch;
            this.sprinting = sprint;
            this.sneaking = sneak;
            this.heldItem = item;
        }
        
        public CompoundNBT save() {
            CompoundNBT nbt = new CompoundNBT();
            nbt.putDouble("X", position.x);
            nbt.putDouble("Y", position.y);
            nbt.putDouble("Z", position.z);
            nbt.putFloat("Yaw", yaw);
            nbt.putFloat("Pitch", pitch);
            nbt.putBoolean("Sprint", sprinting);
            nbt.putBoolean("Sneak", sneaking);
            nbt.putString("Item", heldItem);
            return nbt;
        }
        
        public static EchoFrame load(CompoundNBT nbt) {
            return new EchoFrame(
                new Vector3d(nbt.getDouble("X"), nbt.getDouble("Y"), nbt.getDouble("Z")),
                nbt.getFloat("Yaw"),
                nbt.getFloat("Pitch"),
                nbt.getBoolean("Sprint"),
                nbt.getBoolean("Sneak"),
                nbt.getString("Item")
            );
        }
    }
    
    /**
     * Metadata about a recording for analysis.
     */
    public static class EchoMetadata {
        private double totalDistance = 0;
        private int jumps = 0;
        private int attacks = 0;
        private double avgSpeed = 0;
        private Vector3d lastPos = null;
        private final List<Double> speeds = new ArrayList<>();
        
        public void update(PlayerEntity player) {
            if (lastPos != null) {
                double dist = player.position().distanceTo(lastPos);
                totalDistance += dist;
                speeds.add(dist);
            }
            lastPos = player.position();
        }
        
        public void finalize() {
            if (!speeds.isEmpty()) {
                avgSpeed = speeds.stream().mapToDouble(d -> d).average().orElse(0);
            }
        }
        
        public double getTotalDistance() { return totalDistance; }
        public double getAvgSpeed() { return avgSpeed; }
    }
    
    /**
     * An actively playing echo visible to players.
     */
    public static class ActiveEcho {
        private final EchoRecording recording;
        private final ServerPlayerEntity viewer;
        private int currentFrame = 0;
        private int tickCounter = 0;
        private final EchoRenderer renderer;
        
        public ActiveEcho(EchoRecording recording, ServerPlayerEntity viewer) {
            this.recording = recording;
            this.viewer = viewer;
            this.renderer = new EchoRenderer();
        }
        
        public void tick(ServerWorld world) {
            tickCounter++;
            
            // Play back at half speed
            if (tickCounter % 2 == 0) {
                currentFrame++;
            }
            
            if (currentFrame < recording.getFrames().size()) {
                EchoFrame frame = recording.getFrames().get(currentFrame);
                renderer.render(world, frame, recording);
            }
            
            // Check for death event
            if (currentFrame >= recording.getFrames().size() - 1 && recording.hasDeath()) {
                viewer.displayClientMessage(
                    new StringTextComponent("\"" + recording.getDeathEvent() + "\"")
                        .withStyle(TextFormatting.RED, TextFormatting.ITALIC),
                    false
                );
            }
        }
        
        public boolean isComplete() {
            return currentFrame >= recording.getFrames().size();
        }
        
        public EchoRecording getRecording() { return recording; }
    }
    
    /**
     * Renders echo effects in the world.
     */
    public static class EchoRenderer {
        private final Random random = new Random();
        
        public void render(ServerWorld world, EchoFrame frame, EchoRecording recording) {
            Vector3d pos = frame.position;
            
            // Ghost particles
            world.sendParticles(
                ParticleTypes.SOUL,
                pos.x, pos.y + 0.5, pos.z,
                3, 0.2, 0.3, 0.2, 0.01
            );
            
            // Trail particles if moving
            if (frame.sprinting) {
                world.sendParticles(
                    ParticleTypes.MYCELIUM,
                    pos.x, pos.y, pos.z,
                    2, 0.1, 0, 0.1, 0
                );
            }
            
            // Occasional name flash
            if (random.nextFloat() < 0.02) {
                // Would spawn a name particle or text display
            }
        }
    }
    
    /**
     * Analyzer for finding patterns in echo data.
     */
    public static class EchoPatternAnalyzer {
        
        public static String analyzeRecording(EchoRecording recording) {
            StringBuilder analysis = new StringBuilder();
            analysis.append("Recording Analysis for ").append(recording.getPlayerName()).append("\n");
            
            if (recording.getFrames().isEmpty()) {
                analysis.append("No frames recorded.");
                return analysis.toString();
            }
            
            // Movement analysis
            EchoMetadata meta = recording.metadata;
            analysis.append("Distance traveled: ").append(String.format("%.1f", meta.getTotalDistance())).append(" blocks\n");
            analysis.append("Average speed: ").append(String.format("%.2f", meta.getAvgSpeed())).append(" b/t\n");
            
            // Behavior patterns
            int sprintFrames = 0;
            int sneakFrames = 0;
            for (EchoFrame frame : recording.getFrames()) {
                if (frame.sprinting) sprintFrames++;
                if (frame.sneaking) sneakFrames++;
            }
            
            int total = recording.getFrames().size();
            analysis.append("Sprint ratio: ").append(sprintFrames * 100 / total).append("%\n");
            analysis.append("Sneak ratio: ").append(sneakFrames * 100 / total).append("%\n");
            
            // Death info
            if (recording.hasDeath()) {
                analysis.append("DEATH: ").append(recording.getDeathEvent()).append("\n");
            }
            
            return analysis.toString();
        }
        
        public static EchoRecording findMostDramatic(List<EchoRecording> recordings) {
            EchoRecording mostDramatic = null;
            int highestScore = 0;
            
            for (EchoRecording recording : recordings) {
                int score = 0;
                
                if (recording.hasDeath()) score += 100;
                score += (int) recording.metadata.getTotalDistance();
                
                if (score > highestScore) {
                    highestScore = score;
                    mostDramatic = recording;
                }
            }
            
            return mostDramatic;
        }
    }
}
