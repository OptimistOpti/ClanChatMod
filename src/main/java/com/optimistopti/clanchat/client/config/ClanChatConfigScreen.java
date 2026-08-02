package com.optimistopti.clanchat.client.config;

import com.optimistopti.clanchat.client.ClanChatModClient;
import com.optimistopti.clanchat.network.BackendConnection;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Экран настроек: адрес WebSocket-бэкенда, автоподключение, несколько тумблеров чата,
 * подсказка про переназначение клавиши (через стандартное меню "Управление" Minecraft —
 * Fabric регистрирует туда наш keymapping автоматически).
 */
public class ClanChatConfigScreen extends Screen {

	private final Screen parent;
	private EditBox serverUrlBox;
	private Button connectButton;
	private String statusText = BackendConnection.isConnected() ? "Подключено" : "Отключено";
	private int hintY;

	public ClanChatConfigScreen(Screen parent) {
		super(Component.literal("Настройки ClanChat"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int y = this.height / 2 - 120;

		serverUrlBox = new EditBox(this.font, centerX - 150, y, 220, 20, Component.literal("Адрес бэкенда"));
		serverUrlBox.setMaxLength(256);
		serverUrlBox.setValue(ClanChatConfig.INSTANCE.serverUrl);
		this.addRenderableWidget(serverUrlBox);

		connectButton = Button.builder(Component.literal("Подключиться"), b -> connectNow())
				.bounds(centerX + 75, y, 75, 20).build();
		this.addRenderableWidget(connectButton);
		y += 26;

		BackendConnection.setOnStatusChange(status -> statusText = status);

		addToggle(centerX, y, "Автоподключение при входе в мир",
				() -> ClanChatConfig.INSTANCE.autoConnect,
				v -> ClanChatConfig.INSTANCE.autoConnect = v);
		y += 30;

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
		hintY = y + 30;
	}

	private void connectNow() {
		String url = serverUrlBox.getValue().trim();
		ClanChatConfig.INSTANCE.serverUrl = url;
		ClanChatConfig.save();
		if (url.isBlank()) {
			statusText = "Укажи адрес бэкенда";
			return;
		}
		ClanChatModClient.connect();
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
		int centerX = this.width / 2;
		int topY = this.height / 2 - 120;
		graphics.text(this.font, "Адрес бэкенда (ws://... или wss://...):", centerX - 150, topY - 12, 0xFFAAAAAA, false);
		graphics.text(this.font, "Статус: " + statusText, centerX - 150, topY + 24, 0xFFAAAAAA, false);
		graphics.text(this.font, "Клавишу открытия чата клана можно поменять в",
				centerX - 150, hintY, 0xFF888888, false);
		graphics.text(this.font, "Настройки -> Управление -> ClanChat",
				centerX - 150, hintY + 10, 0xFF888888, false);
	}

	@Override
	public void onClose() {
		ClanChatConfig.INSTANCE.serverUrl = serverUrlBox.getValue().trim();
		ClanChatConfig.save();
		this.minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
