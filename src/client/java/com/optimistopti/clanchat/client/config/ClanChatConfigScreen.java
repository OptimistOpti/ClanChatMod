package com.optimistopti.clanchat.client.config;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Простой экран настроек: несколько тумблеров + подсказка, где переназначить клавишу
 * открытия чата (стандартное меню "Управление" Minecraft — Fabric регистрирует туда
 * наш keymapping автоматически).
 */
public class ClanChatConfigScreen extends Screen {

	private final Screen parent;

	public ClanChatConfigScreen(Screen parent) {
		super(Component.literal("Настройки ClanChat"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int y = this.height / 2 - 70;

		addToggle(centerX, y, "Показывать время сообщений",
				() -> ClanChatConfig.INSTANCE.showTimestamps,
				v -> ClanChatConfig.INSTANCE.showTimestamps = v);
		y += 24;

		addToggle(centerX, y, "Компактный режим",
				() -> ClanChatConfig.INSTANCE.compactMode,
				v -> ClanChatConfig.INSTANCE.compactMode = v);
		y += 24;

		addToggle(centerX, y, "Звук на новое сообщение",
				() -> ClanChatConfig.INSTANCE.playSoundOnMessage,
				v -> ClanChatConfig.INSTANCE.playSoundOnMessage = v);
		y += 24;

		addToggle(centerX, y, "Группировать сообщения по автору",
				() -> ClanChatConfig.INSTANCE.groupMessagesByAuthor,
				v -> ClanChatConfig.INSTANCE.groupMessagesByAuthor = v);
		y += 34;

		this.addRenderableWidget(Button.builder(Component.literal("Готово"), btn -> this.onClose())
				.bounds(centerX - 100, y, 200, 20)
				.build());
	}

	private void addToggle(int centerX, int y, String label, java.util.function.BooleanSupplier getter,
							java.util.function.Consumer<Boolean> setter) {
		this.addRenderableWidget(Button.builder(
						Component.literal(label + ": " + (getter.getAsBoolean() ? "Вкл" : "Выкл")),
						btn -> {
							boolean newValue = !getter.getAsBoolean();
							setter.accept(newValue);
							btn.setMessage(Component.literal(label + ": " + (newValue ? "Вкл" : "Выкл")));
						})
				.bounds(centerX - 120, y, 240, 20)
				.build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.text(this.font, "Клавишу открытия чата клана можно поменять в",
				this.width / 2 - 140, this.height / 2 - 95, 0xFFAAAAAA, false);
		graphics.text(this.font, "Настройки -> Управление -> ClanChat",
				this.width / 2 - 140, this.height / 2 - 85, 0xFFAAAAAA, false);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
