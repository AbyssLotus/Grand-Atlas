package com.seaweed.grandatlas.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.TryFindWaterGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;

/**
 * The Crystal Eel: the user's second Blockbench mob, a small blue eel of the underground waters
 * (crystal caves, the abyssal shores, the Aurora Vault's lake). Its model has a looping swim and a
 * death animation and nothing to fight with, so it is a shy swimmer: it drifts about, darts away
 * from players and flees when hurt, and flops about on land looking for water.
 */
public class CrystalEel extends WaterAnimal {
    /** The Blockbench death animation is 1.29 s; vanilla removes a dead mob after 20 ticks. */
    private static final int DEATH_TICKS = 26;

    public final AnimationState swimAnimationState = new AnimationState();
    public final AnimationState deathAnimationState = new AnimationState();

    public CrystalEel(EntityType<? extends CrystalEel> type, Level level) {
        super(type, level);
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.02F, 0.1F, true);
        this.lookControl = new SmoothSwimmingLookControl(this, 10);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return WaterAnimal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 1.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new TryFindWaterGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.6));
        this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 6.0F, 1.2, 1.6));
        this.goalSelector.addGoal(4, new RandomSwimmingGoal(this, 0.9, 20));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    /** Swims as the dolphin does (its move control steers, this pushes), and sinks a little when idle. */
    @Override
    public void travel(Vec3 input) {
        if (this.isControlledByLocalInstance() && this.isInWater()) {
            this.moveRelative(this.getSpeed(), input);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.9));
            if (this.getTarget() == null) this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.005, 0.0));
        } else {
            super.travel(input);
        }
    }

    /** On land it flops, as fish do. */
    @Override
    public void aiStep() {
        if (!this.isInWater() && this.onGround() && this.verticalCollision && !this.isDeadOrDying()) {
            this.setDeltaMovement(this.getDeltaMovement().add((this.random.nextFloat() * 2.0F - 1.0F) * 0.05F, 0.4F,
                    (this.random.nextFloat() * 2.0F - 1.0F) * 0.05F));
            this.setOnGround(false);
            this.hasImpulse = true;
            this.playSound(SoundEvents.COD_FLOP, this.getSoundVolume(), this.getVoicePitch());
        }
        super.aiStep();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            boolean dying = this.isDeadOrDying();
            this.swimAnimationState.animateWhen(!dying, this.tickCount);
            this.deathAnimationState.animateWhen(dying, this.tickCount);
        }
    }

    /** Long enough for the whole Blockbench death animation. */
    @Override
    protected void tickDeath() {
        this.deathTime++;
        if (this.deathTime >= DEATH_TICKS && !this.level().isClientSide() && !this.isRemoved()) {
            this.level().broadcastEntityEvent(this, (byte) 60);
            this.remove(RemovalReason.KILLED);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.AMETHYST_BLOCK_CHIME;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 240;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.COD_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.COD_DEATH;
    }

    @Override
    protected SoundEvent getSwimSound() {
        return SoundEvents.FISH_SWIM;
    }

    /**
     * Underground water only: deep below sea level (as the glow squid), in water (river water too), and never
     * under open sky. Any light: the crystal caves and the Aurora Vault glow.
     */
    public static boolean checkCrystalEelSpawnRules(EntityType<CrystalEel> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        return pos.getY() <= level.getSeaLevel() - 33 && level.getFluidState(pos).is(FluidTags.WATER) && !level.canSeeSky(pos);
    }
}
