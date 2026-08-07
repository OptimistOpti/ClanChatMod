package com.optimistopti.clanchat.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.optimistopti.clanchat.ClanChatMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Снимает скриншот в исходном разрешении экрана в момент открытия чата (пока ещё видно
 * игровой мир, а не сам GUI) и держит его наготове — если игрок нажмёт кнопку "Скриншот"
 * в чате, прикрепится именно то, что он видел, когда открывал чат, а не сам интерфейс чата.
 * <p>
 * Без даунскейла: при высоком разрешении экрана (1440p/4K) итоговый PNG может весить
 * несколько мегабайт, а в base64 — ещё на треть больше. См. {@link #MAX_BASE64_LENGTH}
 * и не забудь, что бэкенд тоже кладёт это в персистентную историю чата на диск
 * (backend/data/history/*.json) — на хостингах с маленькой дисковой квотой (см. историю
 * с ENOSPC на Wispbyte) это стоит иметь в виду.
 */
public final class ScreenshotCapture {

	/** С запасом под base64 (+~33% к размеру) и служебные поля JSON. Смотри класс-javadoc. */
	private static final int MAX_BASE64_LENGTH = 15_000_000;

	public record PendingScreenshot(String base64Png, int width, int height) {
	}

	private static volatile PendingScreenshot pending;
	private static volatile boolean capturing = false;

	private ScreenshotCapture() {
	}

	public static PendingScreenshot getPending() {
		return pending;
	}

	public static boolean isCapturing() {
		return capturing;
	}

	/** Асинхронно снимает свежий скриншот в исходном разрешении, заменяя предыдущий, когда будет готов. */
	public static void captureNow() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.getMainRenderTarget() == null) {
			return; // не в игре (например, в главном меню) — снимать нечего
		}
		capturing = true;
		try {
			Screenshot.takeScreenshot(mc.getMainRenderTarget(), 1, nativeImage ->
					encodeAndStore(nativeImage, nativeImage.getWidth(), nativeImage.getHeight()));
		} catch (Exception e) {
			ClanChatMod.LOGGER.error("ClanChat: не удалось снять скриншот", e);
			capturing = false;
		}
	}

	/** Кодирование в PNG идёт через временный файл — это тот же проверенный путь, что у ванильного F2. */
	private static void encodeAndStore(NativeImage image, int width, int height) {
		Util.ioPool().execute(() -> {
			Path tempFile = null;
			try {
				tempFile = Files.createTempFile("clanchat_screenshot", ".png");
				image.writeToFile(tempFile);
				byte[] bytes = Files.readAllBytes(tempFile);
				String base64 = Base64.getEncoder().encodeToString(bytes);
				if (base64.length() > MAX_BASE64_LENGTH) {
					ClanChatMod.LOGGER.warn("ClanChat: скриншот получился слишком большим ({} симв.), отменяю", base64.length());
					pending = null;
				} else {
					pending = new PendingScreenshot(base64, width, height);
				}
			} catch (IOException e) {
				ClanChatMod.LOGGER.error("ClanChat: не удалось сохранить скриншот во временный файл", e);
			} finally {
				image.close();
				capturing = false;
				if (tempFile != null) {
					try {
						Files.deleteIfExists(tempFile);
					} catch (IOException ignored) {
					}
				}
			}
		});
	}
}
