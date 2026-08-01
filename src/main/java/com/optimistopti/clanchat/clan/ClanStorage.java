package com.optimistopti.clanchat.clan;

import com.optimistopti.clanchat.ClanChatMod;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Читает/пишет отдельные JSON-файлы кланов в {@code <world>/clanchat/clans/<uuid>.json}.
 * Простое файловое хранилище выбрано вместо {@code SavedData}, чтобы не зависеть
 * от специфики API мира конкретной версии и чтобы данные легко было бэкапить/редактировать руками.
 */
public class ClanStorage {

	private final Path clansDir;

	public ClanStorage(Path worldRoot) {
		this.clansDir = worldRoot.resolve("clanchat").resolve("clans");
		try {
			Files.createDirectories(clansDir);
		} catch (IOException e) {
			ClanChatMod.LOGGER.error("Не удалось создать директорию хранилища кланов: {}", clansDir, e);
		}
	}

	public List<Clan> loadAll() {
		List<Clan> result = new ArrayList<>();
		if (!Files.isDirectory(clansDir)) {
			return result;
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(clansDir, "*.json")) {
			for (Path path : stream) {
				try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
					Clan clan = ClanGson.INSTANCE.fromJson(reader, Clan.class);
					if (clan != null) {
						result.add(clan);
					}
				} catch (IOException | com.google.gson.JsonParseException e) {
					ClanChatMod.LOGGER.error("Не удалось прочитать файл клана {}", path, e);
				}
			}
		} catch (IOException e) {
			ClanChatMod.LOGGER.error("Не удалось перечислить файлы кланов в {}", clansDir, e);
		}
		return result;
	}

	public void save(Clan clan) {
		Path path = clansDir.resolve(clan.getId() + ".json");
		try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
			ClanGson.INSTANCE.toJson(clan, writer);
		} catch (IOException e) {
			ClanChatMod.LOGGER.error("Не удалось сохранить клан {}", clan.getId(), e);
		}
	}

	public void delete(UUID clanId) {
		Path path = clansDir.resolve(clanId + ".json");
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			ClanChatMod.LOGGER.error("Не удалось удалить файл клана {}", clanId, e);
		}
	}
}
