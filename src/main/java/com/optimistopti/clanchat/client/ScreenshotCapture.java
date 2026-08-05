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
 * Снимает уменьшенный скриншот в момент открытия чата (пока ещё видно игровой мир,
 * а не сам GUI) и держит его наготове — если игрок нажмёт кнопку "Скриншот" в чате,
 * прикрепится именно то, что он видел, когда открывал чат, а не сам интерфейс чата.
 */
public final class ScreenshotCapture {

	private static final int TARGET_WIDTH = 400;
	/** С запасом под base64 (+~33% к размеру) и служебные поля JSON. */
	private static final int MAX_BASE64_LENGTH = 350_000;

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

	/** Асинхронно снимает свежий скриншот, заменяя предыдущий, когда будет готов. */
	public static void captureNow() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.getMainRenderTarget() == null) {
			return; // не в игре (например, в главном меню) — снимать нечего
		}
		capturing = true;
		try {
			Screenshot.takeScreenshot(mc.getMainRenderTarget(), 1, nativeImage -> {
				try {
					int targetHeight = Math.max(1, nativeImage.getHeight() * TARGET_WIDTH / Math.max(1, nativeImage.getWidth()));
					NativeImage resized = new NativeImage(TARGET_WIDTH, targetHeight, false);
					nativeImage.resizeSubRectTo(0, 0, nativeImage.getWidth(), nativeImage.getHeight(), resized);
					nativeImage.close();
					encodeAndStore(resized, TARGET_WIDTH, targetHeight);
				} catch (Exception e) {
					ClanChatMod.LOGGER.error("ClanChat: не удалось обработать скриншот", e);
					nativeImage.close();
					capturing = false;
				}
			});
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
