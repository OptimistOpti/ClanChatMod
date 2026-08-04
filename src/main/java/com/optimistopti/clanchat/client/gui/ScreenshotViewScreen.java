package com.optimistopti.clanchat.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.optimistopti.clanchat.ClanChatMod;
import com.optimistopti.clanchat.chat.Attachment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Base64;

/**
 * Полноразмерный (насколько влезает) просмотр скриншота, присланного в чат.
 * Текстура декодируется один раз при открытии и освобождается при закрытии —
 * не держим GPU-текстуры для всех скриншотов в истории одновременно.
 */
public class ScreenshotViewScreen extends Screen {

	private final Screen parent;
	private final Attachment.ScreenshotData data;
	private Identifier textureId;
	private DynamicTexture texture;
	private boolean loadFailed = false;

	public ScreenshotViewScreen(Screen parent, Attachment.ScreenshotData data) {
		super(Component.literal("Скриншот"));
		this.parent = parent;
		this.data = data;
	}

	@Override
	protected void init() {
		this.addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> this.onClose())
				.bounds(this.width / 2 - 50, this.height - 30, 100, 20).build());

		if (texture == null && !loadFailed) {
			try {
				byte[] bytes = Base64.getDecoder().decode(data.imageBase64());
				NativeImage image = NativeImage.read(bytes);
				texture = new DynamicTexture(() -> "clanchat_screenshot_view", image);
				textureId = Identifier.fromNamespaceAndPath(ClanChatMod.MOD_ID, "screenshot_view");
				this.minecraft.getTextureManager().register(textureId, texture);
			} catch (Exception e) {
				ClanChatMod.LOGGER.error("Не удалось декодировать скриншот", e);
				loadFailed = true;
			}
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		if (loadFailed || texture == null) {
			graphics.text(this.font, "Не удалось загрузить скриншот", this.width / 2 - 70, this.height / 2, 0xFFFF5555, false);
			return;
		}

		// Вписываем изображение в доступную область, сохраняя пропорции.
		int maxW = this.width - 40;
		int maxH = this.height - 70;
		float srcAspect = data.width() / (float) Math.max(1, data.height());
		int drawW = maxW;
		int drawH = Math.round(drawW / srcAspect);
		if (drawH > maxH) {
			drawH = maxH;
			drawW = Math.round(drawH * srcAspect);
		}
		int x0 = (this.width - drawW) / 2;
		int y0 = (this.height - drawH) / 2 - 10;

		graphics.blit(textureId, x0, y0, x0 + drawW, y0 + drawH, 0f, 1f, 0f, 1f);
	}

	@Override
	public void onClose() {
		if (textureId != null) {
			this.minecraft.getTextureManager().release(textureId);
			textureId = null;
		}
		texture = null; // TextureManager.release() уже закрывает саму текстуру
		this.minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
