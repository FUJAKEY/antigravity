package com.antigravity.mod.world;

import com.antigravity.mod.AntigravityMod;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * Temporal Distortion Manager
 * Manages time manipulation effects in specific areas of the world.
 * Can slow, speed up, or freeze time for entities in affected zones.
 */
@Mod.EventBusSubscriber(modid = AntigravityMod.MOD_ID)
public class TemporalDistortionManager {
    
    private static final Map<String, TemporalDistortionManager> INSTANCES = new HashMap<>();
    private final List<TemporalZone> activeZones = new ArrayList<>();
    private final Random random = new Random();
    
    public static TemporalDistortionManager get(ServerWorld world) {
        String key = world.dimension().location().toString();
        return INSTANCES.computeIfAbsent(key, k -> new TemporalDistortionManager());
    }
    
    /**
     * Creates a new temporal distortion zone.
     */
    public TemporalZone createZone(BlockPos center, double radius, TemporalEffect effect, int duration) {
        TemporalZone zone = new TemporalZone(center, radius, effect, duration);
        activeZones.add(zone);
        return zone;
    }
    
    /**
     * Main tick method - updates all temporal zones.
     */
    public void tick(ServerWorld world) {
        Iterator<TemporalZone> iterator = activeZones.iterator();
        
        while (iterator.hasNext()) {
            TemporalZone zone = iterator.next();
            zone.tick(world);
            
            if (zone.isExpired()) {
                zone.onExpire(world);
                iterator.remove();
            }
        }
    }
    
    /**
     * Gets all zones affecting a specific position.
     */
    public List<TemporalZone> getZonesAt(BlockPos pos) {
        List<TemporalZone> affecting = new ArrayList<>();
        for (TemporalZone zone : activeZones) {
            if (zone.contains(pos)) {
                affecting.add(zone);
            }
        }
        return affecting;
    }
    
    /**
     * Gets the cumulative time scale at a position.
     */
    public double getTimeScaleAt(BlockPos pos) {
        double scale = 1.0;
        for (TemporalZone zone : getZonesAt(pos)) {
            scale *= zone.getEffect().getTimeScale();
        }
        return scale;
    }
    
    /**
     * Represents a zone with altered time flow.
     */
    public static class TemporalZone {
        private final BlockPos center;
        private final double radius;
        private final TemporalEffect effect;
        private final int maxDuration;
        private int age = 0;
        private double intensity = 1.0;
        private boolean stable = true;
        private final TemporalFluxCalculator fluxCalculator;
        private final EntityTimeTracker entityTracker;
        
        public TemporalZone(BlockPos center, double radius, TemporalEffect effect, int duration) {
            this.center = center;
            this.radius = radius;
            this.effect = effect;
            this.maxDuration = duration;
            this.fluxCalculator = new TemporalFluxCalculator(this);
            this.entityTracker = new EntityTimeTracker();
        }
        
        public void tick(ServerWorld world) {
            age++;
            
            // Update intensity based on zone stability
            if (!stable) {
                intensity += (new Random().nextDouble() - 0.5) * 0.1;
                intensity = Math.max(0.1, Math.min(2.0, intensity));
            }
            
            // Calculate temporal flux
            fluxCalculator.tick();
            
            // Apply effects to entities in zone
            applyEffects(world);
            
            // Spawn particles
            spawnParticles(world);
        }
        
        private void applyEffects(ServerWorld world) {
            AxisAlignedBB bounds = new AxisAlignedBB(center).inflate(radius);
            List<Entity> entities = world.getEntitiesOfClass(Entity.class, bounds);
            
            for (Entity entity : entities) {
                if (!contains(entity.blockPosition())) continue;
                
                // Track entity time
                entityTracker.track(entity);
                
                // Apply temporal effects
                effect.apply(entity, intensity);
                
                // Notify players
                if (entity instanceof PlayerEntity && age == 1) {
                    ((PlayerEntity) entity).displayClientMessage(
                        new StringTextComponent("Time feels... " + effect.getDescription())
                            .withStyle(effect.getColor()),
                        true
                    );
                }
            }
        }
        
        private void spawnParticles(ServerWorld world) {
            if (age % 5 != 0) return;
            
            for (int i = 0; i < 3; i++) {
                double angle = new Random().nextDouble() * Math.PI * 2;
                double dist = new Random().nextDouble() * radius;
                
                double x = center.getX() + 0.5 + Math.cos(angle) * dist;
                double y = center.getY() + 1.0 + new Random().nextDouble() * 2;
                double z = center.getZ() + 0.5 + Math.sin(angle) * dist;
                
                world.sendParticles(
                    effect.getParticle(),
                    x, y, z, 1, 0, 0, 0, 0.02
                );
            }
        }
        
        public void onExpire(ServerWorld world) {
            // Restore all affected entities
            entityTracker.restoreAll();
        }
        
        public boolean contains(BlockPos pos) {
            return center.closerThan(pos, radius);
        }
        
        public boolean isExpired() {
            return age >= maxDuration;
        }
        
        public BlockPos getCenter() { return center; }
        public double getRadius() { return radius; }
        public TemporalEffect getEffect() { return effect; }
        public double getIntensity() { return intensity; }
        public int getAge() { return age; }
        public void setStable(boolean stable) { this.stable = stable; }
    }
    
    /**
     * Types of temporal effects.
     */
    public enum TemporalEffect {
        SLOW(0.5, "distorted", TextFormatting.AQUA) {
            @Override
            public void apply(Entity entity, double intensity) {
                if (entity instanceof LivingEntity) {
                    LivingEntity living = (LivingEntity) entity;
                    int amplifier = (int) (intensity * 2);
                    living.addEffect(new EffectInstance(Effects.MOVEMENT_SLOWDOWN, 40, amplifier));
                    living.addEffect(new EffectInstance(Effects.DIG_SLOWDOWN, 40, amplifier));
                }
                
                // Slow down velocity
                Vector3d velocity = entity.getDeltaMovement();
                entity.setDeltaMovement(velocity.scale(0.5 / intensity));
            }
        },
        
        FAST(2.0, "accelerated", TextFormatting.GOLD) {
            @Override
            public void apply(Entity entity, double intensity) {
                if (entity instanceof LivingEntity) {
                    LivingEntity living = (LivingEntity) entity;
                    int amplifier = (int) (intensity - 1);
                    living.addEffect(new EffectInstance(Effects.MOVEMENT_SPEED, 40, amplifier));
                    living.addEffect(new EffectInstance(Effects.DIG_SPEED, 40, amplifier));
                }
                
                // Speed up velocity
                Vector3d velocity = entity.getDeltaMovement();
                entity.setDeltaMovement(velocity.scale(2.0 * intensity));
            }
        },
        
        FREEZE(0.0, "frozen", TextFormatting.WHITE) {
            @Override
            public void apply(Entity entity, double intensity) {
                // Complete freeze
                entity.setDeltaMovement(Vector3d.ZERO);
                
                if (entity instanceof LivingEntity) {
                    LivingEntity living = (LivingEntity) entity;
                    living.addEffect(new EffectInstance(Effects.MOVEMENT_SLOWDOWN, 40, 10));
                    living.addEffect(new EffectInstance(Effects.DIG_SLOWDOWN, 40, 10));
                }
            }
        },
        
        REWIND(1.0, "rewinding", TextFormatting.LIGHT_PURPLE) {
            @Override
            public void apply(Entity entity, double intensity) {
                // Would need position history to actually rewind
                // For now, just a visual effect
                if (entity instanceof LivingEntity) {
                    LivingEntity living = (LivingEntity) entity;
                    living.addEffect(new EffectInstance(Effects.CONFUSION, 40, 0));
                }
            }
        },
        
        CHAOS(1.0, "chaotic", TextFormatting.RED) {
            @Override
            public void apply(Entity entity, double intensity) {
                // Random time effects
                Random r = new Random();
                if (r.nextFloat() < 0.1) {
                    TemporalEffect[] effects = {SLOW, FAST, FREEZE};
                    effects[r.nextInt(effects.length)].apply(entity, intensity);
                }
            }
        };
        
        private final double timeScale;
        private final String description;
        private final TextFormatting color;
        
        TemporalEffect(double scale, String desc, TextFormatting color) {
            this.timeScale = scale;
            this.description = desc;
            this.color = color;
        }
        
        public abstract void apply(Entity entity, double intensity);
        
        public double getTimeScale() { return timeScale; }
        public String getDescription() { return description; }
        public TextFormatting getColor() { return color; }
        
        public net.minecraft.particles.BasicParticleType getParticle() {
            switch (this) {
                case FREEZE: return ParticleTypes.CLOUD;
                case FAST: return ParticleTypes.FLAME;
                case SLOW: return ParticleTypes.ENCHANT;
                case REWIND: return ParticleTypes.REVERSE_PORTAL;
                case CHAOS: return ParticleTypes.DRAGON_BREATH;
                default: return ParticleTypes.ENCHANT;
            }
        }
    }
    
    /**
     * Calculates temporal flux variations within a zone.
     */
    public static class TemporalFluxCalculator {
        private final TemporalZone zone;
        private double fluxLevel = 0.0;
        private double fluxDelta = 0.0;
        private final List<Double> fluxHistory = new ArrayList<>();
        
        public TemporalFluxCalculator(TemporalZone zone) {
            this.zone = zone;
        }
        
        public void tick() {
            // Calculate flux based on zone age and stability
            double baseFlux = Math.sin(zone.getAge() / 20.0) * 0.2;
            double instabilityFlux = zone.stable ? 0 : new Random().nextGaussian() * 0.1;
            
            fluxDelta = baseFlux + instabilityFlux - fluxLevel;
            fluxLevel += fluxDelta * 0.1;
            
            // Record history
            fluxHistory.add(fluxLevel);
            if (fluxHistory.size() > 100) {
                fluxHistory.remove(0);
            }
        }
        
        public double getFluxLevel() { return fluxLevel; }
        public double getFluxDelta() { return fluxDelta; }
        
        public double getAverageFlux() {
            if (fluxHistory.isEmpty()) return 0;
            return fluxHistory.stream().mapToDouble(d -> d).average().orElse(0);
        }
        
        public boolean isStabilizing() {
            return Math.abs(fluxDelta) < 0.01;
        }
    }
    
    /**
     * Tracks entity states for potential time reversion.
     */
    public static class EntityTimeTracker {
        private final Map<UUID, EntityTimeState> states = new HashMap<>();
        private final int maxHistorySize = 100;
        
        public void track(Entity entity) {
            EntityTimeState state = states.computeIfAbsent(entity.getUUID(), 
                uuid -> new EntityTimeState());
            state.record(entity);
        }
        
        public void restoreAll() {
            // Would restore entities to previous states
            states.clear();
        }
        
        private static class EntityTimeState {
            private final List<StateSnapshot> history = new ArrayList<>();
            
            public void record(Entity entity) {
                StateSnapshot snapshot = new StateSnapshot(
                    entity.position(),
                    entity.getDeltaMovement(),
                    entity instanceof LivingEntity ? ((LivingEntity) entity).getHealth() : 0
                );
                
                history.add(snapshot);
                
                // Limit history size
                while (history.size() > 100) {
                    history.remove(0);
                }
            }
            
            public StateSnapshot getState(int ticksAgo) {
                int index = history.size() - 1 - ticksAgo;
                if (index < 0 || index >= history.size()) return null;
                return history.get(index);
            }
        }
        
        private static class StateSnapshot {
            public final Vector3d position;
            public final Vector3d velocity;
            public final float health;
            
            public StateSnapshot(Vector3d pos, Vector3d vel, float health) {
                this.position = pos;
                this.velocity = vel;
                this.health = health;
            }
        }
    }
    
    /**
     * Utility for creating common temporal zone configurations.
     */
    public static class TemporalZoneFactory {
        
        public static TemporalZone createSlowField(BlockPos center, double radius, int duration) {
            return new TemporalZone(center, radius, TemporalEffect.SLOW, duration);
        }
        
        public static TemporalZone createFreezeZone(BlockPos center, double radius, int duration) {
            TemporalZone zone = new TemporalZone(center, radius, TemporalEffect.FREEZE, duration);
            zone.setStable(false); // Freeze zones are inherently unstable
            return zone;
        }
        
        public static TemporalZone createChaosRift(BlockPos center, double radius, int duration) {
            TemporalZone zone = new TemporalZone(center, radius, TemporalEffect.CHAOS, duration);
            zone.setStable(false);
            return zone;
        }
        
        public static TemporalZone createAccelerationField(BlockPos center, double radius, int duration) {
            return new TemporalZone(center, radius, TemporalEffect.FAST, duration);
        }
    }
}
