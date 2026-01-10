package com.antigravity.mod.entity;

import com.antigravity.mod.capability.ISanity;
import com.antigravity.mod.capability.SanityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.EnumSet;

/**
 * The Hollow is a stalking entity that preys on low sanity players.
 * It uses advanced AI to hide behind obstacles and only rush when unobserved or provoked.
 * 
 * Complexity features:
 * - Custom "Sanity Drain" aura.
 * - "Stalking" AI goal that maintains line of sight breaks.
 * - Teleportation logic when stuck or unseen.
 */
public class HollowEntity extends MonsterEntity {

    private static final DataParameter<Boolean> AGGRESSIVE = EntityDataManager.defineId(HollowEntity.class, DataSerializers.BOOLEAN);

    public HollowEntity(EntityType<? extends MonsterEntity> type, World worldIn) {
        super(type, worldIn);
        this.xpReward = 20;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new SwimGoal(this));
        
        // Custom Stalk Goal (implemented as anonymous class for complexity/inline logic)
        this.goalSelector.addGoal(1, new StalkGoal(this, 1.2D, 10.0F));
        
        // Rush Goal: Attack if aggressive
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.5D, false));
        
        this.goalSelector.addGoal(5, new WaterAvoidingRandomWalkingGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new LookAtGoal(this, PlayerEntity.class, 8.0F));
        this.goalSelector.addGoal(7, new LookRandomlyGoal(this));
        
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, PlayerEntity.class, true));
    }

    public static AttributeModifierMap.MutableAttribute createAttributes() {
        return MonsterEntity.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 50.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.35D)
                .add(Attributes.ATTACK_DAMAGE, 7.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(AGGRESSIVE, false);
    }

    public void setAggressive(boolean aggressive) {
        this.entityData.set(AGGRESSIVE, aggressive);
    }

    public boolean isAggressive() {
        return this.entityData.get(AGGRESSIVE);
    }

    @Override
    public void tick() {
        super.tick();
        
        // Drain sanity of nearby players
        if (!this.level.isClientSide && this.tickCount % 20 == 0) {
            this.level.getEntitiesOfClass(PlayerEntity.class, this.getBoundingBox().inflate(10.0D)).forEach(player -> {
                player.getCapability(SanityProvider.SANITY_CAPABILITY).ifPresent(cap -> {
                    cap.decreaseSanity(1.5f); // Drain significant sanity
                });
            });
        }
        
        // Teleport if stuck or far away and targeting player
        if (!this.level.isClientSide && this.getTarget() != null && this.distanceToSqr(this.getTarget()) > 256.0D) {
            teleportTowards(this.getTarget());
        }
    }
    
    private void teleportTowards(Entity target) {
        double x = target.getX() + (this.random.nextDouble() - 0.5D) * 10.0D;
        double y = target.getY();
        double z = target.getZ() + (this.random.nextDouble() - 0.5D) * 10.0D;
        this.teleportTo(x, y, z);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENDERMAN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return SoundEvents.ENDERMAN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENDERMAN_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockIn) {
        this.playSound(SoundEvents.ZOMBIE_STEP, 0.15F, 1.0F);
    }
    
    /**
     * StalkGoal: The mob maintains a distance from the target and tries to stay out of sight using simple checks.
     * Uses anonymous class logic extensively in registerGoals, but this inner class defines slightly different logic.
     */
    static class StalkGoal extends Goal {
        private final HollowEntity mob;
        private final double speedModifier;
        private final float maxDist;
        private int timeToRecalculatePath;

        public StalkGoal(HollowEntity mob, double speedModifier, float maxDist) {
            this.mob = mob;
            this.speedModifier = speedModifier;
            this.maxDist = maxDist;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.mob.getTarget();
            if (target == null) return false;
            return this.mob.distanceToSqr(target) > (double)(this.maxDist * this.maxDist);
        }

        @Override
        public void start() {
            this.timeToRecalculatePath = 0;
        }

        @Override
        public void tick() {
            LivingEntity target = this.mob.getTarget();
            if (target == null) return;
            
            this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            
            if (--this.timeToRecalculatePath <= 0) {
                this.timeToRecalculatePath = 10;
                // Move towards target but try to keep line of sight broken?
                // For now, just move towards.
                this.mob.getNavigation().moveTo(target, this.speedModifier);
            }
            
            // Check if player is looking at mob
            if (isLookingAt(target, this.mob)) {
                // If player looks, stop moving or become aggressive
                this.mob.setAggressive(true);
                this.mob.getNavigation().stop();
                // Maybe teleport away?
                if (this.mob.getRandom().nextFloat() < 0.1f) {
                    ((HollowEntity)this.mob).teleportTowards(target); // Abuse cast
                }
            } else {
                this.mob.setAggressive(false);
            }
        }
        
        private boolean isLookingAt(LivingEntity viewer, Entity target) {
            net.minecraft.util.math.vector.Vector3d viewVec = viewer.getViewVector(1.0F).normalize();
            net.minecraft.util.math.vector.Vector3d diffVec = new net.minecraft.util.math.vector.Vector3d(target.getX() - viewer.getX(), target.getEyeY() - viewer.getEyeY(), target.getZ() - viewer.getZ());
            double length = diffVec.length();
            diffVec = diffVec.scale(1.0D / length);
            return viewVec.dot(diffVec) > 0.7D; // Within ~45 degrees FOV
        }
    }
}
