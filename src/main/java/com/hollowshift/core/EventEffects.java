package com.hollowshift.core;

import com.hollowshift.director.EventDef;
import com.hollowshift.entity.HollowSpawner;
import com.hollowshift.net.ModPayloads;
import com.hollowshift.registry.ModItems;
import com.hollowshift.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * What each Director event actually does. Every effect follows one rule:
 * nothing changes while the player is looking at it.
 *
 * Each effect returns true if it happened. If it couldn't (no torches nearby, no door
 * in the base), the Director still spent the budget; the moment simply passes.
 */
public final class EventEffects {

    private static final Map<String, Predicate<ServerPlayer>> EFFECTS = new HashMap<>();

    private static final String[] SIGN_LINES = {"it was here", "not alone", "home?", "don't look", "behind you", "i saw you"};

    static {
        // ---------------- ambient ----------------
        EFFECTS.put("twig_snap", p -> soundBehind(p, SoundEvents.WOOD_BREAK, 18 + p.getRandom().nextInt(10), 0.35f, 1.7f));
        EFFECTS.put("leaves_rustle", p -> soundBehind(p, SoundEvents.AZALEA_LEAVES_STEP, 8 + p.getRandom().nextInt(6), 0.6f, 0.7f));
        EFFECTS.put("delayed_footstep", EventEffects::delayedFootstep);
        EFFECTS.put("cave_breathing", p -> soundBehind(p, ModSounds.BREATHING, 5, 0.5f, 1.0f));
        EFFECTS.put("peripheral_decay", p -> { ModPayloads.cue(p, "decay", 14); return true; });
        EFFECTS.put("distant_figure", HollowSpawner::distantFigure);

        // ---------------- tampering ----------------
        EFFECTS.put("extinguish_torch", EventEffects::extinguishTorch);
        EFFECTS.put("fake_cow", FakeMobs::spawnCow);
        EFFECTS.put("mimic_mining", EventEffects::mimicMining);
        EFFECTS.put("breadcrumb_reverse", EventEffects::reverseTorches);
        EFFECTS.put("door_left_open", EventEffects::openBaseDoor);
        EFFECTS.put("chest_sorted", EventEffects::sortChest);
        EFFECTS.put("sign_rewritten", EventEffects::rewriteSign);
        EFFECTS.put("bed_moved", EventEffects::wakeFacingWrongWay);

        // ---------------- meta ----------------
        EFFECTS.put("lying_subtitle", p -> {
            SoundEvent lie = ModSounds.LIES[p.getRandom().nextInt(ModSounds.LIES.length)];
            return soundBehind(p, lie, 3, 1.0f, 1.0f);
        });
        EFFECTS.put("inventory_unlit_torch", EventEffects::burnOutOneTorch);
        EFFECTS.put("fake_join_message", p -> {
            // The real vanilla join message, in its real colour, with the player's own name.
            p.sendSystemMessage(Component.translatable("multiplayer.player.joined", p.getDisplayName())
                    .withStyle(ChatFormatting.YELLOW));
            return true;
        });
        EFFECTS.put("fake_saving_screen", p -> {
            ModPayloads.cue(p, "saving", 50 + p.getRandom().nextInt(30));
            // When the screen goes away, you're not quite where you were.
            Vec3 back = p.position().subtract(Sight.flatLook(p).scale(1.5));
            if (p.serverLevel().noCollision(p, p.getBoundingBox().move(back.subtract(p.position())))) {
                p.teleportTo(p.serverLevel(), back.x, p.getY(), back.z, p.getYRot() + 25, p.getXRot());
            }
            return true;
        });
        EFFECTS.put("name_whisper", p -> {
            soundBehind(p, ModSounds.WHISPER, 1.5, 0.45f, 1.0f);
            p.displayClientMessage(Component.literal(p.getGameProfile().getName().toLowerCase())
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), true);
            return true;
        });
        EFFECTS.put("the_quiet", p -> true); // the silence was the whole event

        // ---------------- confrontation ----------------
        EFFECTS.put("close_encounter", p -> HollowSpawner.stalker(p, false));
        EFFECTS.put("tunnel_stalk", p -> HollowSpawner.stalker(p, false));
        EFFECTS.put("takes_something", p -> HollowSpawner.stalker(p, true));
        EFFECTS.put("base_presence", HollowSpawner::inBase);
        EFFECTS.put("daylight_visitor", HollowSpawner::daylightVisitor);
    }

    private EventEffects() {}

    public static boolean run(ServerPlayer player, EventDef event) {
        Predicate<ServerPlayer> fx = EFFECTS.get(event.id);
        return fx != null && fx.test(player);
    }

    public static boolean has(String id) {
        return EFFECTS.containsKey(id);
    }

    // ------------------------------------------------------------------------

    /** Your own footstep, half a beat late, from right behind you. */
    private static boolean delayedFootstep(ServerPlayer p) {
        BlockState under = p.serverLevel().getBlockState(p.blockPosition().below());
        return soundBehind(p, under.getSoundType().getStepSound(), 2.5, 0.35f, 1.0f);
    }

    private static boolean mimicMining(ServerPlayer p) {
        for (int i = 0; i < 3; i++) {
            soundBehind(p, SoundEvents.STONE_HIT, 12, 0.5f, 0.8f + i * 0.05f);
        }
        return soundBehind(p, SoundEvents.STONE_BREAK, 12, 0.5f, 0.9f);
    }

    /** A torch you can't currently see goes out, leaving a wisp of smoke. */
    private static boolean extinguishTorch(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        List<BlockPos> torches = find(p, p.blockPosition(), 14, 6,
                (pos, s) -> (s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH)) && !Sight.inView(p, pos));
        if (torches.isEmpty()) return false;
        BlockPos pos = torches.get(p.getRandom().nextInt(torches.size()));
        level.removeBlock(pos, false);
        level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5,
                6, 0.05, 0.05, 0.05, 0.01);
        soundAt(p, SoundEvents.FIRE_EXTINGUISH, Vec3.atCenterOf(pos), 0.25f, 1.4f);
        return true;
    }

    /** The wall torches you placed to find your way out now lean the other way. */
    private static boolean reverseTorches(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        List<BlockPos> torches = find(p, p.blockPosition(), 24, 8,
                (pos, s) -> s.is(Blocks.WALL_TORCH) && !Sight.inView(p, pos));
        int flipped = 0;
        for (BlockPos pos : torches) {
            BlockState s = level.getBlockState(pos);
            Direction facing = s.getValue(WallTorchBlock.FACING);
            Direction opposite = facing.getOpposite();
            // Only flip if there is a wall on the other side to hang from.
            BlockPos support = pos.relative(facing);
            if (level.getBlockState(support).isFaceSturdy(level, support, opposite)) {
                level.setBlockAndUpdate(pos, s.setValue(WallTorchBlock.FACING, opposite));
                if (++flipped >= 4) break;
            }
        }
        return flipped > 0;
    }

    /** A closed door in your base is open when you get back. */
    private static boolean openBaseDoor(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        List<BlockPos> doors = find(p, Base.center(p), 12, 4, (pos, s) -> s.getBlock() instanceof DoorBlock
                && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                && !s.getValue(DoorBlock.OPEN)
                && !Sight.inView(p, pos));
        if (doors.isEmpty()) return false;
        BlockPos pos = doors.get(0);
        BlockState s = level.getBlockState(pos);
        ((DoorBlock) s.getBlock()).setOpen(null, level, s, pos, true);
        return true;
    }

    /** Someone has been through a chest and put everything in order. */
    private static boolean sortChest(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        for (BlockPos pos : find(p, Base.center(p), 12, 4, (pos, s) -> s.is(Blocks.CHEST) || s.is(Blocks.BARREL))) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof Container chest) || chest.isEmpty()) continue;
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < chest.getContainerSize(); i++) {
                if (!chest.getItem(i).isEmpty()) items.add(chest.getItem(i).copy());
                chest.setItem(i, ItemStack.EMPTY);
            }
            items.sort(Comparator.comparing((ItemStack s) -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString())
                    .thenComparing(s -> -s.getCount()));
            for (int i = 0; i < items.size(); i++) chest.setItem(i, items.get(i));
            chest.setChanged();
            return true;
        }
        return false;
    }

    /** One line on a sign in your base now says something you didn't write. */
    private static boolean rewriteSign(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        for (BlockPos pos : find(p, Base.center(p), 14, 5, (pos, s) -> !Sight.inView(p, pos)
                && level.getBlockEntity(pos) instanceof SignBlockEntity)) {
            SignBlockEntity sign = (SignBlockEntity) level.getBlockEntity(pos);
            SignText text = sign.getFrontText();
            int line = p.getRandom().nextInt(4);
            String msg = SIGN_LINES[p.getRandom().nextInt(SIGN_LINES.length)];
            sign.setText(text.setMessage(line, Component.literal(msg)), true);
            sign.setChanged();
            BlockState s = level.getBlockState(pos);
            level.sendBlockUpdated(pos, s, s, 3);
            return true;
        }
        return false;
    }

    /** You wake up facing the other way. */
    private static boolean wakeFacingWrongWay(ServerPlayer p) {
        p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), p.getYRot() + 180, p.getXRot());
        return true;
    }

    /** One torch in your inventory is now a burnt-out stub. */
    private static boolean burnOutOneTorch(ServerPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(Items.TORCH)) {
                stack.shrink(1);
                ItemStack burnt = new ItemStack(ModItems.BURNT_TORCH);
                if (!inv.add(burnt)) p.drop(burnt, false);
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------

    private interface BlockTest { boolean test(BlockPos pos, BlockState state); }

    private static List<BlockPos> find(ServerPlayer p, BlockPos center, int r, int ry, BlockTest test) {
        ServerLevel level = p.serverLevel();
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos pos : BlockPos.withinManhattan(center, r, ry, r)) {
            if (test.test(pos, level.getBlockState(pos))) out.add(pos.immutable());
        }
        return out;
    }

    /** A sound only this player hears, a given distance behind them. */
    public static boolean soundBehind(ServerPlayer p, SoundEvent sound, double distance, float volume, float pitch) {
        Vec3 where = p.position().subtract(Sight.flatLook(p).scale(distance)).add(0, 1, 0);
        return soundAt(p, sound, where, volume, pitch);
    }

    public static boolean soundAt(ServerPlayer p, SoundEvent sound, Vec3 where, float volume, float pitch) {
        p.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                SoundSource.HOSTILE, where.x, where.y, where.z, volume, pitch, p.getRandom().nextLong()));
        return true;
    }
}
