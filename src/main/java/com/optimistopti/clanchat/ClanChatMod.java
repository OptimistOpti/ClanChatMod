package com.optimistopti.clanchat;

import com.optimistopti.clanchat.clan.ClanManager;
import com.optimistopti.clanchat.chat.ClanChatHistory;
import com.optimistopti.clanchat.command.ClanCommands;
import com.optimistopti.clanchat.network.ClanChatNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClanChatMod implements ModInitializer {
	public static final String MOD_ID = "clanchat";
	public static final Logger LOGGER = LoggerFactory.getLogger("ClanChat");

	/** Не null пока сервер запущен (integrated или dedicated). Создаётся в SERVER_STARTED. */
	private static ClanManager clanManager;
	private static final ClanChatHistory CHAT_HISTORY = new ClanChatHistory();
	/**
	 * Кэшируем сам инстанс сервера здесь, а не тянем его через ServerPlayer#getServer() —
	 * та цепочка методов слишком чувствительна к точным маппингам конкретной версии.
	 * Так весь остальной код зависит только от этого класса.
	 */
	private static MinecraftServer server;

	@Override
	public void onInitialize() {
		LOGGER.info("ClanChat: инициализация...");

		ClanChatNetworking.registerPayloadTypes();
		ClanChatNetworking.registerServerReceivers();
		ClanCommands.register();

		ServerLifecycleEvents.SERVER_STARTED.register(startedServer -> {
			server = startedServer;
			clanManager = new ClanManager(startedServer);
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(stoppingServer -> {
			if (clanManager != null) {
				clanManager.saveAll();
			}
			clanManager = null;
			server = null;
		});

		LOGGER.info("ClanChat: готово.");
	}

	public static ClanManager getClanManager() {
		return clanManager;
	}

	public static MinecraftServer getServer() {
		return server;
	}

	public static ClanChatHistory getChatHistory() {
		return CHAT_HISTORY;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
