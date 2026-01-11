package com.antigravity.mod.entity;

import net.minecraft.block.BlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import java.util.*;

/**
 * Shadow Entity
 * A creature of pure darkness that phase-shifts between solid and ethereal forms.
 * Stronger in darkness, weakened and hurt by light.
 */
public class ShadowEntity extends MonsterEntity {
    
    // Data parameters
    private static final DataParameter<Boolean> ETHEREAL = EntityDataManager.defineId(
        ShadowEntity.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Integer> DARKNESS_POWER = EntityDataManager.defineId(
        ShadowEntity.class, DataSerializers.INT);
    private static final DataParameter<Boolean> ENRAGED = EntityDataManager.defineId(
        ShadowEntity.class, DataSerializers.BOOLEAN);
    
    // State tracking
    private final ShadowBehaviorController behaviorController;
    private final DarknessPowerSystem powerSystem;
    private final PhaseShiftAbility phaseShiftAbility;
    private int lightDamageCooldown = 0;
    private BlockPos homePosition = null;
    private final Random random = new Random();
    
    public ShadowEntity(EntityType<? extends MonsterEntity> type, World world) {
        super(type, world);
        this.behaviorController = new ShadowBehaviorController(this);
        this.powerSystem = new DarknessPowerSystem(this);
        this.phaseShiftAbility = new PhaseShiftAbility(this);
    }
    
    public static AttributeModifierMap.MutableAttribute createAttributes() {
        return MonsterEntity.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 60.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.35D)
            .add(Attributes.ATTACK_DAMAGE, 8.0D)
            .add(Attributes.FOLLOW_RANGE, 48.0D)
            .add(Attributes.ARMOR, 4.0D);
    }
    
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ETHEREAL, false);
        this.entityData.define(DARKNESS_POWER, 0);
        this.entityData.define(ENRAGED, false);
    }
    
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new SwimGoal(this));
        this.goalSelector.addGoal(1, new ShadowMeleeAttackGoal(this, 1.2D, false));
        this.goalSelector.addGoal(2, new ShadowStalkGoal(this));
        this.goalSelector.addGoal(3, new SeekDarknessGoal(this));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomWalkingGoal(this, 1.0D));
        this.goalSelector.addGoal(5, new LookAtGoal(this, PlayerEntity.class, 8.0F));
        this.goalSelector.addGoal(6, new LookRandomlyGoal(this));
        
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, PlayerEntity.class, true));
    }
    
    @Override
    public void tick() {
        super.tick();
        
        behaviorController.tick();
        powerSystem.tick();
        phaseShiftAbility.tick();
        
        // Light damage handling
        if (lightDamageCooldown > 0) lightDamageCooldown--;
        handleLightDamage();
        
        // Particle effects
        if (level.isClientSide) {
            spawnAmbientParticles();
        }
    }
    
    /**
     * Handle damage from light sources.
     */
    private void handleLightDamage() {
        if (level.isClientSide || lightDamageCooldown > 0) return;
        
        int lightLevel = level.getBrightness(LightType.BLOCK, blockPosition());
        int skyLight = level.getBrightness(LightType.SKY, blockPosition());
        
        // Daylight is extremely dangerous
        if (skyLight > 10 && level.isDay()) {
            takeLightDamage(4.0f);
            setSecondsOnFire(2);
            lightDamageCooldown = 20;
        }
        // Artificial light causes moderate damage
        else if (lightLevel > 10) {
            takeLightDamage(2.0f);
            lightDamageCooldown = 40;
        }
        // Some light causes discomfort
        else if (lightLevel > 6) {
            powerSystem.reducePower(5);
        }
    }
    
    private void takeLightDamage(float amount) {
        // Ethereal form takes less light damage
        if (isEthereal()) {
            amount *= 0.5f;
        }
        
        hurt(DamageSource.MAGIC, amount);
        
        // Flee response
        if (getTarget() != null) {
            LivingEntity target = getTarget();
            Vector3d away = position().subtract(target.position()).normalize();
            setDeltaMovement(away.x * 0.5, 0.2, away.z * 0.5);
        }
    }
    
    private void spawnAmbientParticles() {
        if (random.nextFloat() < 0.3f) {
            double x = getX() + (random.nextDouble() - 0.5) * getBbWidth();
            double y = getY() + random.nextDouble() * getBbHeight();
            double z = getZ() + (random.nextDouble() - 0.5) * getBbWidth();
            
            if (isEthereal()) {
                level.addParticle(ParticleTypes.ENCHANT, x, y, z, 0, 0.05, 0);
            } else {
                level.addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0.02, 0);
            }
        }
    }
    
    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Ethereal shadows take reduced physical damage
        if (isEthereal() && !source.isMagic() && !source.isFire()) {
            amount *= 0.3f;
        }
        
        // Fire damage is extra effective
        if (source.isFire()) {
            amount *= 1.5f;
        }
        
        // May trigger phase shift on damage
        if (random.nextFloat() < 0.3f && !phaseShiftAbility.isOnCooldown()) {
            phaseShiftAbility.activate();
        }
        
        return super.hurt(source, amount);
    }
    
    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        
        if (hit && target instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) target;
            
            // Apply darkness effect
            living.addEffect(new EffectInstance(Effects.BLINDNESS, 60, 0));
            
            // Steal light/life force
            powerSystem.absorbEnergy(10);
            
            // Chance to apply weakness
            if (random.nextFloat() < 0.3f) {
                living.addEffect(new EffectInstance(Effects.WEAKNESS, 100, 0));
            }
        }
        
        return hit;
    }
    
    public boolean isEthereal() {
        return this.entityData.get(ETHEREAL);
    }
    
    public void setEthereal(boolean ethereal) {
        this.entityData.set(ETHEREAL, ethereal);
        
        // Change collision behavior
        this.noPhysics = ethereal;
    }
    
    public boolean isEnraged() {
        return this.entityData.get(ENRAGED);
    }
    
    public void setEnraged(boolean enraged) {
        this.entityData.set(ENRAGED, enraged);
    }
    
    public int getDarknessPower() {
        return this.entityData.get(DARKNESS_POWER);
    }
    
    public void setDarknessPower(int power) {
        this.entityData.set(DARKNESS_POWER, Math.max(0, Math.min(100, power)));
    }
    
    @Override
    public void addAdditionalSaveData(CompoundNBT nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putBoolean("Ethereal", isEthereal());
        nbt.putInt("DarknessPower", getDarknessPower());
        nbt.putBoolean("Enraged", isEnraged());
        if (homePosition != null) {
            nbt.putInt("HomeX", homePosition.getX());
            nbt.putInt("HomeY", homePosition.getY());
            nbt.putInt("HomeZ", homePosition.getZ());
        }
    }
    
    @Override
    public void readAdditionalSaveData(CompoundNBT nbt) {
        super.readAdditionalSaveData(nbt);
        setEthereal(nbt.getBoolean("Ethereal"));
        setDarknessPower(nbt.getInt("DarknessPower"));
        setEnraged(nbt.getBoolean("Enraged"));
        if (nbt.contains("HomeX")) {
            homePosition = new BlockPos(nbt.getInt("HomeX"), nbt.getInt("HomeY"), nbt.getInt("HomeZ"));
        }
    }
    
    /**
     * Controls the shadow's behavior state machine.
     */
    public static class ShadowBehaviorController {
        private final ShadowEntity shadow;
        private ShadowState currentState = ShadowState.LURKING;
        private int stateTimer = 0;
        
        public ShadowBehaviorController(ShadowEntity shadow) {
            this.shadow = shadow;
        }
        
        public void tick() {
            stateTimer++;
            
            ShadowState newState = evaluateState();
            if (newState != currentState) {
                exitState(currentState);
                currentState = newState;
                enterState(currentState);
                stateTimer = 0;
            }
            
            tickState(currentState);
        }
        
        private ShadowState evaluateState() {
            // Check for danger (light damage)
            if (shadow.isOnFire()) {
                return ShadowState.FLEEING;
            }
            
            // Check for target
            LivingEntity target = shadow.getTarget();
            if (target != null) {
                double distance = shadow.distanceToSqr(target);
                
                if (distance < 4) {
                    return ShadowState.ATTACKING;
                } else if (distance < 64) {
                    return ShadowState.STALKING;
                }
            }
            
            // Default behaviors
            if (shadow.getDarknessPower() > 80) {
                return ShadowState.EMPOWERED;
            }
            
            return ShadowState.LURKING;
        }
        
        private void enterState(ShadowState state) {
            switch (state) {
                case FLEEING:
                    shadow.phaseShiftAbility.activate();
                    break;
                case EMPOWERED:
                    shadow.setEnraged(true);
                    break;
                default:
                    break;
            }
        }
        
        private void exitState(ShadowState state) {
            if (state == ShadowState.EMPOWERED) {
                shadow.setEnraged(false);
            }
        }
        
        private void tickState(ShadowState state) {
            switch (state) {
                case LURKING:
                    // Passive power regeneration
                    if (stateTimer % 20 == 0) {
                        shadow.powerSystem.regenerate(1);
                    }
                    break;
                case STALKING:
                    // Become ethereal when stalking
                    if (!shadow.isEthereal() && stateTimer > 40) {
                        shadow.setEthereal(true);
                    }
                    break;
                case ATTACKING:
                    // Become solid to attack
                    if (shadow.isEthereal()) {
                        shadow.setEthereal(false);
                    }
                    break;
                default:
                    break;
            }
        }
        
        public ShadowState getCurrentState() { return currentState; }
    }
    
    public enum ShadowState {
        LURKING,
        STALKING,
        ATTACKING,
        FLEEING,
        EMPOWERED
    }
    
    /**
     * Manages the shadow's power level based on ambient darkness.
     */
    public static class DarknessPowerSystem {
        private final ShadowEntity shadow;
        private int power = 50;
        
        public DarknessPowerSystem(ShadowEntity shadow) {
            this.shadow = shadow;
        }
        
        public void tick() {
            // Calculate ambient darkness
            int blockLight = shadow.level.getBrightness(LightType.BLOCK, shadow.blockPosition());
            int skyLight = shadow.level.getBrightness(LightType.SKY, shadow.blockPosition());
            
            int effectiveLight = Math.max(blockLight, shadow.level.isDay() ? skyLight : 0);
            int darknessLevel = 15 - effectiveLight;
            
            // Regenerate in darkness
            if (darknessLevel > 10) {
                regenerate(2);
            } else if (darknessLevel > 5) {
                regenerate(1);
            }
            
            // Drain in light
            if (effectiveLight > 10) {
                reducePower(3);
            }
            
            shadow.setDarknessPower(power);
        }
        
        public void regenerate(int amount) {
            power = Math.min(100, power + amount);
        }
        
        public void reducePower(int amount) {
            power = Math.max(0, power - amount);
        }
        
        public void absorbEnergy(int amount) {
            regenerate(amount);
            shadow.heal(amount / 5.0f);
        }
        
        public int getPower() { return power; }
    }
    
    /**
     * Ability to shift into ethereal form.
     */
    public static class PhaseShiftAbility {
        private final ShadowEntity shadow;
        private int cooldown = 0;
        private int duration = 0;
        private static final int MAX_DURATION = 100;
        private static final int COOLDOWN_TIME = 200;
        
        public PhaseShiftAbility(ShadowEntity shadow) {
            this.shadow = shadow;
        }
        
        public void tick() {
            if (cooldown > 0) cooldown--;
            
            if (duration > 0) {
                duration--;
                if (duration == 0) {
                    shadow.setEthereal(false);
                }
            }
        }
        
        public void activate() {
            if (cooldown > 0) return;
            
            shadow.setEthereal(true);
            duration = MAX_DURATION;
            cooldown = COOLDOWN_TIME;
            
            // Teleport to a nearby dark location
            teleportToShadow();
        }
        
        private void teleportToShadow() {
            for (int attempt = 0; attempt < 10; attempt++) {
                int x = shadow.random.nextInt(16) - 8;
                int y = shadow.random.nextInt(8) - 4;
                int z = shadow.random.nextInt(16) - 8;
                
                BlockPos newPos = shadow.blockPosition().offset(x, y, z);
                
                int light = shadow.level.getBrightness(LightType.BLOCK, newPos);
                if (light < 5 && shadow.level.getBlockState(newPos).isAir()) {
                    shadow.teleportTo(newPos.getX() + 0.5, newPos.getY(), newPos.getZ() + 0.5);
                    return;
                }
            }
        }
        
        public boolean isOnCooldown() { return cooldown > 0; }
    }
    
    /**
     * Custom attack goal that incorporates shadow behaviors.
     */
    public static class ShadowMeleeAttackGoal extends MeleeAttackGoal {
        private final ShadowEntity shadow;
        
        public ShadowMeleeAttackGoal(ShadowEntity shadow, double speedMod, boolean followUnseen) {
            super(shadow, speedMod, followUnseen);
            this.shadow = shadow;
        }
        
        @Override
        public boolean canUse() {
            return super.canUse() && !shadow.isEthereal();
        }
    }
    
    /**
     * Goal to stalk players from the shadows.
     */
    public static class ShadowStalkGoal extends Goal {
        private final ShadowEntity shadow;
        private PlayerEntity target;
        
        public ShadowStalkGoal(ShadowEntity shadow) {
            this.shadow = shadow;
        }
        
        @Override
        public boolean canUse() {
            LivingEntity entityTarget = shadow.getTarget();
            if (entityTarget instanceof PlayerEntity) {
                this.target = (PlayerEntity) entityTarget;
                return shadow.distanceToSqr(target) > 25 && shadow.distanceToSqr(target) < 256;
            }
            return false;
        }
        
        @Override
        public void tick() {
            if (target == null) return;
            
            // Move towards target but maintain distance
            double distance = shadow.distanceToSqr(target);
            
            if (distance > 100) {
                shadow.getNavigation().moveTo(target, 1.0);
            } else if (distance < 36) {
                // Too close, find cover
                BlockPos cover = findCover();
                if (cover != null) {
                    shadow.getNavigation().moveTo(cover.getX(), cover.getY(), cover.getZ(), 1.0);
                }
            }
        }
        
        private BlockPos findCover() {
            // Find a dark position with cover
            for (int i = 0; i < 10; i++) {
                int x = shadow.random.nextInt(10) - 5;
                int z = shadow.random.nextInt(10) - 5;
                
                BlockPos pos = shadow.blockPosition().offset(x, 0, z);
                BlockPos behind = pos.relative(target.getDirection().getOpposite());
                
                if (shadow.level.getBlockState(behind).getMaterial().isSolid() &&
                    shadow.level.getBrightness(LightType.BLOCK, pos) < 7) {
                    return pos;
                }
            }
            return null;
        }
    }
    
    /**
     * Goal to seek out dark areas.
     */
    public static class SeekDarknessGoal extends Goal {
        private final ShadowEntity shadow;
        private BlockPos targetPos;
        
        public SeekDarknessGoal(ShadowEntity shadow) {
            this.shadow = shadow;
        }
        
        @Override
        public boolean canUse() {
            int currentLight = shadow.level.getBrightness(LightType.BLOCK, shadow.blockPosition());
            return currentLight > 7;
        }
        
        @Override
        public void start() {
            targetPos = findDarkSpot();
        }
        
        @Override
        public void tick() {
            if (targetPos != null) {
                shadow.getNavigation().moveTo(targetPos.getX(), targetPos.getY(), targetPos.getZ(), 1.2);
            }
        }
        
        private BlockPos findDarkSpot() {
            for (int i = 0; i < 20; i++) {
                int x = shadow.random.nextInt(32) - 16;
                int y = shadow.random.nextInt(8) - 4;
                int z = shadow.random.nextInt(32) - 16;
                
                BlockPos pos = shadow.blockPosition().offset(x, y, z);
                
                if (shadow.level.getBlockState(pos).isAir() &&
                    shadow.level.getBrightness(LightType.BLOCK, pos) < 5) {
                    return pos;
                }
            }
            return null;
        }
    }
}
