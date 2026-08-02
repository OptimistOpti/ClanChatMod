package com.optimistopti.clanchat;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Мод целиком клиентский (см. "environment": "client" в fabric.mod.json) — вся клановая
 * логика живёт на отдельном WebSocket-бэкенде (см. /backend в репозитории), а не на
 * Minecraft-сервере. Этот класс — просто держатель общих констант.
 */
public final class ClanChatMod {
	public static final String MOD_ID = "clanchat";
	public static final Logger LOGGER = LoggerFactory.getLogger("ClanChat");

	private ClanChatMod() {
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
