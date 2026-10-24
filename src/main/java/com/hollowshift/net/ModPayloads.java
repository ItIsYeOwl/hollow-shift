package com.hollowshift.net;

import com.hollowshift.HollowShift;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Server -> client messages. The server owns the game state; the client draws and plays it. */
public final class ModPayloads {

    /** The player's current tension, sent every second. Drives fog, screen effects and ambience. */
    public record TensionSync(float tension) implements CustomPacketPayload {
        public static final Type<TensionSync> TYPE = new Type<>(HollowShift.id("tension"));
        public static final StreamCodec<ByteBuf, TensionSync> CODEC =
                StreamCodec.composite(ByteBufCodecs.FLOAT, TensionSync::tension, TensionSync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * A one-off client effect.
     *   "silence"  mute ambient sound and music for `ticks`
     *   "decay"    the periphery of the screen rots for a moment
     *   "saving"   a fake "Saving world..." screen for `ticks`
     */
    public record Cue(String cue, int ticks) implements CustomPacketPayload {
        public static final Type<Cue> TYPE = new Type<>(HollowShift.id("cue"));
        public static final StreamCodec<ByteBuf, Cue> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Cue::cue,
                ByteBufCodecs.VAR_INT, Cue::ticks,
                Cue::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private ModPayloads() {}

    public static void register() {
        PayloadTypeRegistry.playS2C().register(TensionSync.TYPE, TensionSync.CODEC);
        PayloadTypeRegistry.playS2C().register(Cue.TYPE, Cue.CODEC);
    }

    public static void cue(ServerPlayer player, String cue, int ticks) {
        if (ServerPlayNetworking.canSend(player, Cue.TYPE)) {
            ServerPlayNetworking.send(player, new Cue(cue, ticks));
        }
    }
}
