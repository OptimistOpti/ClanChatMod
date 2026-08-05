package com.optimistopti.clanchat.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.optimistopti.clanchat.ClanChatMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Кэш decoded-текстур миниатюр скриншотов, ключ — id сообщения. Специально статический
 * (не привязан к конкретному экземпляру {@code MessageListWidget}) — экран клана
 * пересобирает виджет на каждое структурное изменение состояния ({@code tick()} в
 * {@code ClanChatScreen}), и если бы кэш жил в самом виджете, старые GPU-текстуры
 * просто терялись бы (утечка) при каждой такой пересборке вместо освобождения.
 * <p>
 * Ограничен {@link #MAX_ENTRIES} записями — самая старая по последнему обращению
 * вытесняется и её текстура освобождается через {@code TextureManager#release}.
 */
public final class ScreenshotThumbnailCache {

	private static final int MAX_ENTRIES = 24;

	private static final Map<UUID, Identifier> CACHE = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<UUID, Identifier> eldest) {
			if (size() > MAX_ENTRIES) {
				Minecraft.getInstance().getTextureManager().release(eldest.getValue());
				return true;
			}
			return false;
		}
	};

	private ScreenshotThumbnailCache() {
	}

	/** Возвращает уже зарегистрированную текстуру, либо декодирует и регистрирует новую. */
	public static Identifier getOrCreate(UUID messageId, String base64Png) {
		Identifier existing = CACHE.get(messageId);
		if (existing != null) {
			return existing;
		}
		try {
			byte[] bytes = Base64.getDecoder().decode(base64Png);
			NativeImage image = NativeImage.read(bytes);
			DynamicTexture texture = new DynamicTexture(() -> "clanchat_thumb", image);
			Identifier id = Identifier.fromNamespaceAndPath(ClanChatMod.MOD_ID,
					"screenshot_thumb_" + messageId.toString().replace("-", ""));
			Minecraft.getInstance().getTextureManager().register(id, texture);
			CACHE.put(messageId, id);
			return id;
		} catch (Exception e) {
			ClanChatMod.LOGGER.error("Не удалось декодировать миниатюру скриншота", e);
			return null;
		}
	}
}
