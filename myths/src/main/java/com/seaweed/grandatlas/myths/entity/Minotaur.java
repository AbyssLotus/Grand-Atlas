package com.seaweed.grandatlas.myths.entity;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.level.Level;

/**
 * The Minotaur, guardian of every Daedalus's Lab.
 *
 * <p><b>A stand-in for now:</b> it extends vanilla's {@link Ravager}, so it borrows the
 * ravager's body, charge, roar and AI, and draws with the ravager's model (client/MythsClient).
 * When the Blockbench model is ready, only the renderer changes. Stronger than a ravager, it
 * never despawns, and it shows a boss bar to players who can see it. It drops the labrys
 * (loot_table/entities/minotaur.json).
 */
public class Minotaur extends Ravager {

    private final ServerBossEvent bossBar = new ServerBossEvent(
            getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);

    public Minotaur(EntityType<? extends Minotaur> type, Level level) {
        super(type, level);
        this.xpReward = 60;
    }

    /** Its stats; registered in MythsEntities.onAttributes. A ravager has 100 health and 12 attack. */
    public static AttributeSupplier.Builder createAttributes() {
        return Ravager.createAttributes()
                .add(Attributes.MAX_HEALTH, 220.0)
                .add(Attributes.ATTACK_DAMAGE, 16.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        bossBar.setProgress(getHealth() / getMaxHealth());
        // When the maze shifts, a wall can close over a Minotaur whose lab moved away. It
        // vanishes into the stone instead of slowly suffocating (and dropping a free labrys).
        if (isInWall()) discard();
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    /** Guardians stay put: never despawn, however far away the players are. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    /** Not part of any raid, even though the ravager it's built on is a raider. */
    @Override
    public boolean canJoinRaid() {
        return false;
    }
}
