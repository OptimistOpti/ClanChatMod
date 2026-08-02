package com.optimistopti.clanchat.network;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Считает тот же UUID, что и ванильный Minecraft в offline-mode:
 * {@code UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(UTF_8))}.
 * Это стандартный публичный алгоритм (не зависит от версии/маппингов), используется
 * только для стабильной идентификации на нашем бэкенде — не имеет отношения к реальной
 * авторизации Mojang.
 */
public final class OfflineUuid {
	private OfflineUuid() {
	}

	public static UUID fromName(String name) {
		return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
	}
}
