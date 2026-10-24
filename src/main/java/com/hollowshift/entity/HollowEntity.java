package com.hollowshift.entity;

import com.hollowshift.HollowConfig;
import com.hollowshift.core.Sight;
import com.hollowshift.core.TensionSystem;
import com.hollowshift.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * The Hollow. A tall, featureless figure the Director places into the world for a single moment,
 * then removes. It is never saved with the world, never drops anything, and cannot be killed:
 * hitting it just makes it go away.
 *
 * Modes:
 *   OBSERVE  stands far away and watches. Gone once you've seen it and look away, or get close.
 *   STALK    only moves while you are NOT looking at it. Freezes the instant you look.
 *            Refuses to step into bright light. Reaching you is a catch.
 *   THIEF    stalks the same way, but a catch takes your torches and held item.
 *   LURK     stands motionless inside your base. Vanishes after you notice it.
 *   VISITOR  stands in the open in broad daylight and makes no attempt to hide.
 */
public class HollowEntity extends Monster {

    public enum Mode { OBSERVE, STALK, THIEF, LURK, VISITOR }

    private Mode mode = Mode.OBSERVE;
    private UUID targetId;
    private int age;
    private int maxAge = 20 * 60;
    private int seenTicks;
    private boolean everSeen;

    public HollowEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FOLLOW_RANGE, 96)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    public HollowEntity setup(Mode mode, ServerPlayer target, int lifetimeTicks) {
        this.mode = mode;
        this.targetId = target.getUUID();
        this.maxAge = lifetimeTicks;
        this.lookAt(target, 360, 360);
        return this;
    }

    public Mode mode() {
        return mode;
    }

    // No AI goals: everything is driven from tick() so behaviour is exact.
    @Override
    protected void registerGoals() {}

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        age++;
        ServerPlayer target = targetId == null ? null
                : (ServerPlayer) ((ServerLevel) level()).getPlayerByUUID(targetId);
        if (target == null || !target.isAlive() || target.isSpectator() || age > maxAge
                || target.level() != level() || distanceTo(target) > 160) {
            vanish(false);
            return;
        }

        boolean watched = Sight.isWatching(target, this);
        if (watched) {
            seenTicks++;
            everSeen = true;
        }

        switch (mode) {
            case OBSERVE -> observe(target, watched);
            case STALK, THIEF -> stalk(target, watched);
            case LURK -> lurk(target, watched);
            case VISITOR -> visitor(target, watched);
        }
    }

    private void observe(ServerPlayer target, boolean watched) {
        stand(target, watched);
        // Seen, then the player looks away or something blocks the view: it's gone.
        if (everSeen && !watched) vanish(false);
        else if (seenTicks > 60 || distanceTo(target) < 20) vanish(false);
    }

    private void stalk(ServerPlayer target, boolean watched) {
        // "It can't cross lit ground."
        if (level().getBrightness(LightLayer.BLOCK, blockPosition()) >= 11) {
            vanish(true);
            return;
        }
        if (watched) {
            // Frozen mid-step. No head turn, no drift.
            getNavigation().stop();
            setDeltaMovement(0, getDeltaMovement().y, 0);
            return;
        }
        double speed = distanceTo(target) > 16 ? 1.9 : 1.3;
        getNavigation().moveTo(target, speed);
        getLookControl().setLookAt(target, 30, 30);

        if (distanceTo(target) < 1.6) catchPlayer(target);
    }

    private void lurk(ServerPlayer target, boolean watched) {
        stand(target, watched);
        if (seenTicks > 25) vanish(true);
    }

    private void visitor(ServerPlayer target, boolean watched) {
        stand(target, watched);
        if (everSeen && !watched && age % 20 == 0) vanish(false);
        if (distanceTo(target) < 12) vanish(true);
    }

    /** Still, facing the player. When watched it doesn't even turn its head. */
    private void stand(ServerPlayer target, boolean watched) {
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        if (!watched) getLookControl().setLookAt(target, 10, 10);
    }

    private void catchPlayer(ServerPlayer p) {
        HollowConfig cfg = HollowConfig.get();
        level().playSound(null, p.blockPosition(), ModSounds.HOLLOW_CATCH, SoundSource.HOSTILE, 1.0f, 1.0f);
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0, false, false));
        if (mode == Mode.THIEF && cfg.stealItems) {
            steal(p);
        } else if (cfg.catchDamage > 0) {
            p.hurt(damageSources().mobAttack(this), cfg.catchDamage);
        }
        TensionSystem.add(p, 20);
        vanish(false);
    }

    /** All torches are gone. The held item lands somewhere in the dark behind you. */
    private void steal(ServerPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.TORCH) || s.is(Items.SOUL_TORCH) || s.is(Items.LANTERN)) inv.setItem(i, ItemStack.EMPTY);
        }
        ItemStack held = p.getMainHandItem();
        if (!held.isEmpty()) {
            Vec3 drop = p.position().subtract(Sight.flatLook(p).scale(10));
            BlockPos ground = level().getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(drop));
            if (Math.abs(ground.getY() - p.getY()) > 8) ground = BlockPos.containing(drop.x, p.getY(), drop.z);
            ItemEntity item = new ItemEntity(level(), ground.getX() + 0.5, ground.getY() + 0.5, ground.getZ() + 0.5,
                    held.copy());
            item.setPickUpDelay(40);
            level().addFreshEntity(item);
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    private void vanish(boolean audible) {
        if (audible) {
            level().playSound(null, blockPosition(), ModSounds.HOLLOW_VANISH, SoundSource.HOSTILE, 0.6f, 1.0f);
        }
        discard();
    }

    // ---- It can't be fought, saved, pushed or looted ----

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            vanish(true);
        }
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false; // tick() handles its own lifetime
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (mode == Mode.STALK || mode == Mode.THIEF) {
            playSound(ModSounds.HOLLOW_STEP, 0.35f, 0.9f + random.nextFloat() * 0.2f);
        }
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false; // the dread doesn't care about difficulty
    }
}
