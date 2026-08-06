package com.pedrodalben.bigbangid.professorcarvalho;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.pedrodalben.bigbangid.professorcarvalho.lifecycle.GatewayRuntime;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BigBangIdProfessorGatewayMod implements ModInitializer {
    public static final String MOD_ID = "bigbangid_professorcarvalhogateway";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final GatewayRuntime RUNTIME = new GatewayRuntime();

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(RUNTIME::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> RUNTIME.shutdown());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> RUNTIME.playerJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> RUNTIME.playerLeave(handler.player));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("professor")
                .then(Commands.literal("vincular").then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<net.minecraft.commands.CommandSourceStack, String>argument("codigo", StringArgumentType.word()).executes(context -> { RUNTIME.link(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "codigo")); return 1; })))
                .then(Commands.literal("sincronizar").executes(context -> { RUNTIME.syncProfile(context.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("perfil").executes(context -> { RUNTIME.profile(context.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("status").executes(context -> { context.getSource().sendSuccess(() -> Component.literal(RUNTIME.statusText()), false); return 1; }))
                .then(Commands.literal("ajuda").executes(context -> { context.getSource().sendSuccess(() -> Component.literal("Professor Carvalho: /professor vincular <codigo>, /professor sincronizar, /professor perfil, /professor status"), false); return 1; })));
            dispatcher.register(Commands.literal("bigbangid").redirect(dispatcher.getRoot().getChild("professor")));
            dispatcher.register(Commands.literal("pcgateway").requires(source -> source.hasPermission(3))
                .then(Commands.literal("status").executes(context -> { context.getSource().sendSuccess(() -> Component.literal(RUNTIME.statusText()), false); return 1; }))
                .then(Commands.literal("reload").executes(context -> { RUNTIME.reload(); context.getSource().sendSuccess(() -> Component.literal("Configuração recarregada."), true); return 1; }))
                .then(Commands.literal("queue").executes(context -> { context.getSource().sendSuccess(() -> Component.literal("Eventos pendentes: " + RUNTIME.pending()), true); return 1; }))
                .then(Commands.literal("deadletter").executes(context -> { context.getSource().sendSuccess(() -> Component.literal("Eventos em dead-letter: " + RUNTIME.deadLetter()), true); return 1; }))
                .then(Commands.literal("retry").executes(context -> { RUNTIME.retry(); context.getSource().sendSuccess(() -> Component.literal("Fila de eventos reprocessada."), true); return 1; }))
                .then(Commands.literal("test").executes(context -> { RUNTIME.testHeartbeat(); context.getSource().sendSuccess(() -> Component.literal("Heartbeat de teste enfileirado."), true); return 1; })));
        });
        LOGGER.info("{} inicializado como mod server-side.", MOD_ID);
    }

    public static GatewayRuntime runtime() { return RUNTIME; }
}
