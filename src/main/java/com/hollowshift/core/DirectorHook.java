package com.hollowshift.core;

import com.hollowshift.HollowConfig;
import com.hollowshift.HollowShift;
import com.hollowshift.director.Decision;
import com.hollowshift.director.DefaultEvents;
import com.hollowshift.director.Director;
import com.hollowshift.director.DirectorContext;
import com.hollowshift.director.DirectorState;
import com.hollowshift.director.EventDef;
import com.hollowshift.director.Tier;
import com.hollowshift.director.Trigger;
import com.hollowshift.net.ModPayloads;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/** Runs the Director for every player and carries out what it decides. */
public final class DirectorHook {

    /** Set with /hollowshift debug. Prints every Director decision in chat. */
    public static boolean debug = false;

    private static Director director;

    private static final AttachmentType<String> STATE = AttachmentRegistry.<String>builder()
            .persistent(Codec.STRING)
            .copyOnDeath()
            .buildAndRegister(HollowShift.id("director_state"));

    private static final Map<UUID, Boolean> WAS_IN_BASE = new HashMap<>();

    private DirectorHook() {}

    public static Director director() {
        return director;
    }

    public static void register() {
        HollowConfig cfg = HollowConfig.get();
        List<EventDef> events = DefaultEvents.all().stream()
                .filter(e -> cfg.metaScares || e.tier != Tier.META)
                .toList();
        director = new Director(cfg.director, events, new Random());

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % director.config().evalIntervalTicks != 0) return;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.isSpectator() || p.isCreative()) continue;
                evaluate(p);
            }
        });

        // Waking up in the morning is a discovery moment.
        EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
            if (entity instanceof ServerPlayer p && p.level().getDayTime() % 24000 < 1000) {
                DirectorState s = load(p);
                act(p, director.onTrigger(Trigger.ON_WAKE, context(p), s));
                save(p, s);
            }
        });

        // Nobody sleeps through a silence. The vanilla "monsters nearby" message, with none in sight.
        EntitySleepEvents.ALLOW_SLEEPING.register((player, pos) -> {
            if (player instanceof ServerPlayer sp && load(sp).isPendingSilence()) {
                return Player.BedSleepingProblem.NOT_SAFE;
            }
            return null;
        });
    }

    private static void evaluate(ServerPlayer p) {
        DirectorContext ctx = context(p);
        DirectorState s = load(p);

        boolean wasHome = WAS_IN_BASE.getOrDefault(p.getUUID(), ctx.inBase());
        WAS_IN_BASE.put(p.getUUID(), ctx.inBase());
        if (!wasHome && ctx.inBase()) {
            act(p, director.onTrigger(Trigger.ON_RETURN_TO_BASE, ctx, s));
        }

        act(p, director.tick(ctx, s));
        save(p, s);
    }

    private static void act(ServerPlayer p, Optional<Decision> decision) {
        decision.ifPresent(d -> {
            switch (d.kind()) {
                case BEGIN_SILENCE -> {
                    ModPayloads.cue(p, "silence", d.silenceTicks());
                    log(p, "silence " + d.silenceTicks() / 20 + "s, then " + d.event().id);
                }
                case FIRE -> {
                    boolean happened = EventEffects.run(p, d.event());
                    if (happened) TensionSystem.add(p, d.event().tensionDelta);
                    log(p, d.event().id + " (" + d.event().tier.name().toLowerCase() + ")"
                            + (happened ? "" : " - nothing suitable nearby"));
                }
            }
        });
    }

    private static void log(ServerPlayer p, String msg) {
        if (debug) p.sendSystemMessage(Component.literal("[director] " + msg).withStyle(ChatFormatting.DARK_GRAY));
    }

    public static DirectorContext context(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        BlockPos eyes = BlockPos.containing(p.getEyePosition());
        long dayTime = level.getDayTime();
        return new DirectorContext(
                level.getGameTime(),
                dayTime / 24000 + 1,
                (int) (dayTime % 24000),
                TensionSystem.get(p),
                !level.canSeeSky(eyes) && eyes.getY() < level.getSeaLevel(),
                Base.isInBase(p),
                level.getBrightness(LightLayer.BLOCK, eyes) >= 10);
    }

    public static DirectorState load(ServerPlayer p) {
        return DirectorState.decode(p.getAttached(STATE));
    }

    public static void save(ServerPlayer p, DirectorState s) {
        p.setAttached(STATE, s.encode());
    }

    public static void reset(ServerPlayer p) {
        p.removeAttached(STATE);
    }
}
