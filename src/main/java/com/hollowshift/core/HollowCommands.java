package com.hollowshift.core;

import com.hollowshift.director.DirectorContext;
import com.hollowshift.director.DirectorState;
import com.hollowshift.director.EventDef;
import com.hollowshift.net.ModPayloads;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Testing tools (operators only):
 *   /hollowshift status            tension, day, budget, phase
 *   /hollowshift tension <0-100>   set your tension
 *   /hollowshift fire <event>      run any event right now, skipping the Director
 *   /hollowshift debug <on|off>    print every Director decision in chat
 *   /hollowshift reset             wipe your Director memory (cooldowns, history)
 */
public final class HollowCommands {

    private HollowCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("hollowshift")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("status").executes(HollowCommands::status))
                        .then(Commands.literal("tension")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                        .executes(ctx -> {
                                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                                            TensionSystem.set(p, IntegerArgumentType.getInteger(ctx, "value"));
                                            return reply(ctx, "Tension set to " + (int) TensionSystem.get(p));
                                        })))
                        .then(Commands.literal("fire")
                                .then(Commands.argument("event", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                DirectorHook.director().events().stream().map(e -> e.id), b))
                                        .executes(HollowCommands::fire)))
                        .then(Commands.literal("debug")
                                .then(Commands.argument("on", BoolArgumentType.bool()).executes(ctx -> {
                                    DirectorHook.debug = BoolArgumentType.getBool(ctx, "on");
                                    return reply(ctx, "Director debug " + (DirectorHook.debug ? "on" : "off"));
                                })))
                        .then(Commands.literal("reset").executes(ctx -> {
                            DirectorHook.reset(ctx.getSource().getPlayerOrException());
                            return reply(ctx, "Director memory cleared");
                        }))));
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        DirectorContext c = DirectorHook.context(p);
        DirectorState s = DirectorHook.load(p);
        return reply(ctx, String.format(
                "Day %d | tension %.0f | budget %.1f/%.1f | intensity %.0f | %s%s | %s%s%s",
                c.day(), c.tension(), s.budget, DirectorHook.director().config().dailyBudget(c.day()),
                s.intensity, s.phase, s.isPendingSilence() ? " (silence)" : "",
                c.inBase() ? "home" : "away", c.underground() ? ", underground" : "", c.nearLight() ? ", lit" : ", dark"));
    }

    private static int fire(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(ctx, "event");
        EventDef ev = DirectorHook.director().byId(id);
        if (ev == null) return reply(ctx, "No event called " + id);
        if (ev.needsSilence) ModPayloads.cue(p, "silence", 60);
        boolean ok = EventEffects.run(p, ev);
        return reply(ctx, ok ? "Fired " + id : id + " couldn't happen here (needs a different spot)");
    }

    private static int reply(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }
}
