package com.optimistopti.clanchat.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Точка входа для ModMenu (см. entrypoint "modmenu" в fabric.mod.json).
 * Если ModMenu не установлен — этот класс просто никогда не загружается,
 * мод от этого не ломается (см. "suggests" вместо "depends" в fabric.mod.json).
 */
public class ClanChatModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ClanChatConfigScreen::new;
	}
}
