package com.seaweed.grandatlas.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Potted Monster: a snapping plant in a terracotta pot. The model (the user's Blockbench
 * "Learning.bbmodel") has a looping idle sway and a lunge-and-snap attack, and no walk, so it is an
 * ambusher: it sits still among the giant mushrooms passing for a potted plant, wanders only now
 * and then, and when a player comes close it shuffles after them on its pot and bites.
 */
public class PottedMonster extends Monster {
    /** Entity event: the attack lunge, sent to clients when it bites (as the ravager and iron golem use 4). */
    private static final byte ATTACK_EVENT = 4;
    /** The Blockbench attack is 0.5 s long. */
    private static final int ATTACK_TICKS = 10;

    public final AnimationState idleAnimationState = new AnimationState();
    public final AnimationState attackAnimationState = new AnimationState();
    private int attackAnimationTicks;

    public PottedMonster(EntityType<? extends PottedMonster> type, Level level) {
        super(type, level);
        this.xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 16.0)
                .add(Attributes.ARMOR, 2.0)
                // a pot full of earth: hard to knock about
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, false));
        // Rarely moves on its own: an interval of 240 ticks against the usual 120.
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8, 0.001F) {
            {
                setInterval(240);
            }
        });
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        this.level().broadcastEntityEvent(this, ATTACK_EVENT);
        this.playSound(SoundEvents.FOX_BITE, 1.0F, 0.6F + this.random.nextFloat() * 0.2F);
        return super.doHurtTarget(target);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == ATTACK_EVENT) {
            this.attackAnimationTicks = ATTACK_TICKS;
            this.idleAnimationState.stop();
            this.attackAnimationState.start(this.tickCount);
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            // The two Blockbench animations both pose the stem and head from scratch, so one plays at a time.
            if (this.attackAnimationTicks > 0 && --this.attackAnimationTicks == 0) this.attackAnimationState.stop();
            this.idleAnimationState.animateWhen(!this.attackAnimationState.isStarted(), this.tickCount);
        }
    }

    /**
     * Neutral everywhere. A monster's default prefers the dark, and the natural spawner refuses a
     * spot it rates below zero, which is any lit spot: in the glowing fungal forests that would
     * undo the any-light spawn rule (ModEntities).
     */
    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return 0.0F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BIG_DRIPLEAF_TILT_UP;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.DECORATED_POT_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.DECORATED_POT_SHATTER;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.DECORATED_POT_STEP, 0.4F, 1.0F);
    }
}
