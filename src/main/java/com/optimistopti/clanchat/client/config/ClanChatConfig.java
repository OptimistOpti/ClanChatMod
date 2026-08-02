package com.optimistopti.clanchat.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.optimistopti.clanchat.ClanChatMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Настройки самого мода (не путать с переназначением клавиши — это делается через
 * стандартное меню "Управление" Minecraft, оно же доступно из этого экрана как ссылка).
 */
public class ClanChatConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("clanchat.json");

	public static ClanChatConfig INSTANCE = new ClanChatConfig();

	public boolean showTimestamps = true;
	public boolean compactMode = false;
	public boolean playSoundOnMessage = true;
	public boolean groupMessagesByAuthor = true;

	/** Адрес WebSocket-бэкенда, например ws://your-wispbyte-host:8080 или wss://... за прокси с TLS. */
	public String serverUrl = "";
	/** Подключаться автоматически при входе в мир, если адрес задан. */
	public boolean autoConnect = true;

	public static void load() {
		if (Files.exists(PATH)) {
			try (var reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
				ClanChatConfig loaded = GSON.fromJson(reader, ClanChatConfig.class);
				if (loaded != null) {
					INSTANCE = loaded;
					return;
				}
			} catch (IOException | com.google.gson.JsonParseException e) {
				ClanChatMod.LOGGER.error("Не удалось прочитать clanchat.json, использую значения по умолчанию", e);
			}
		}
		INSTANCE = new ClanChatConfig();
		save();
	}

	public static void save() {
		try (var writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
			GSON.toJson(INSTANCE, writer);
		} catch (IOException e) {
			ClanChatMod.LOGGER.error("Не удалось сохранить clanchat.json", e);
		}
	}
}
