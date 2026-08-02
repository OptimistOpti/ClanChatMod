package com.optimistopti.clanchat.client.config;

import com.optimistopti.clanchat.client.ClanChatModClient;
import com.optimistopti.clanchat.network.BackendConnection;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Экран настроек: адрес WebSocket-бэкенда, автоподключение, тумблеры чата в две колонки,
 * размер шрифта, отправка по Enter, всплывающие уведомления (размер + угол экрана).
 * Переназначение клавиши открытия чата — через стандартное меню "Управление" Minecraft,
 * Fabric регистрирует туда наш keymapping автоматически (ссылка внизу экрана).
 */
public class ClanChatConfigScreen extends Screen {

	private static final float[] FONT_SCALES = {0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
	private static final float[] POPUP_SCALES = {0.75f, 1.0f, 1.25f, 1.5f};
	private static final String[] POPUP_POSITIONS = {"TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT"};

	private final Screen parent;
	private EditBox serverUrlBox;
	private String statusText = BackendConnection.isConnected() ? "Подключено" : "Отключено";
	private int hintY;

	public ClanChatConfigScreen(Screen parent) {
		super(Component.literal("Настройки ClanChat"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int y = this.height / 2 - 130;

		serverUrlBox = new EditBox(this.font, centerX - 150, y, 220, 20, Component.literal("Адрес бэкенда"));
		serverUrlBox.setMaxLength(256);
		serverUrlBox.setValue(ClanChatConfig.INSTANCE.serverUrl);
		this.addRenderableWidget(serverUrlBox);

		this.addRenderableWidget(Button.builder(Component.literal("Подключиться"), b -> connectNow())
				.bounds(centerX + 75, y, 75, 20).build());
		y += 30;

		BackendConnection.setOnStatusChange(status -> statusText = status);

		int leftX = centerX - 245;
		int rightX = centerX + 5;
		int rowY = y;

		addToggle(leftX, rowY, "Автоподключение",
				() -> ClanChatConfig.INSTANCE.autoConnect, v -> ClanChatConfig.INSTANCE.autoConnect = v);
		addToggle(rightX, rowY, "Показывать время",
				() -> ClanChatConfig.INSTANCE.showTimestamps, v -> ClanChatConfig.INSTANCE.showTimestamps = v);
		rowY += 24;

		addToggle(leftX, rowY, "Компактный режим",
				() -> ClanChatConfig.INSTANCE.compactMode, v -> ClanChatConfig.INSTANCE.compactMode = v);
		addToggle(rightX, rowY, "Звук на сообщение",
				() -> ClanChatConfig.INSTANCE.playSoundOnMessage, v -> ClanChatConfig.INSTANCE.playSoundOnMessage = v);
		rowY += 24;

		addToggle(leftX, rowY, "Группировать по автору",
				() -> ClanChatConfig.INSTANCE.groupMessagesByAuthor, v -> ClanChatConfig.INSTANCE.groupMessagesByAuthor = v);
		addToggle(rightX, rowY, "Отправка по Enter",
				() -> ClanChatConfig.INSTANCE.sendOnEnter, v -> ClanChatConfig.INSTANCE.sendOnEnter = v);
		rowY += 24;

		addFloatCycle(leftX, rowY, "Размер шрифта", FONT_SCALES,
				() -> ClanChatConfig.INSTANCE.fontScale, v -> ClanChatConfig.INSTANCE.fontScale = v);
		addToggle(rightX, rowY, "Всплыв. уведомления",
				() -> ClanChatConfig.INSTANCE.showPopupNotifications, v -> ClanChatConfig.INSTANCE.showPopupNotifications = v);
		rowY += 24;

		addFloatCycle(leftX, rowY, "Размер уведомления", POPUP_SCALES,
				() -> ClanChatConfig.INSTANCE.popupScale, v -> ClanChatConfig.INSTANCE.popupScale = v);
		addStringCycle(rightX, rowY, "Позиция уведомления", POPUP_POSITIONS, ClanChatConfigScreen::positionLabel,
				() -> ClanChatConfig.INSTANCE.popupPosition, v -> ClanChatConfig.INSTANCE.popupPosition = v);
		rowY += 34;

		this.addRenderableWidget(Button.builder(Component.literal("Готово"), btn -> this.onClose())
				.bounds(centerX - 100, rowY, 200, 20)
				.build());
		hintY = rowY + 30;
	}

	private static String positionLabel(String key) {
		return switch (key) {
			case "TOP_LEFT" -> "Слева сверху";
			case "TOP_RIGHT" -> "Справа сверху";
			case "BOTTOM_LEFT" -> "Слева снизу";
			case "BOTTOM_RIGHT" -> "Справа снизу";
			default -> key;
		};
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

	private void addToggle(int x, int y, String label, BooleanSupplier getter, Consumer<Boolean> setter) {
		this.addRenderableWidget(Button.builder(
						Component.literal(label + ": " + (getter.getAsBoolean() ? "Вкл" : "Выкл")),
						btn -> {
							boolean newValue = !getter.getAsBoolean();
							setter.accept(newValue);
							btn.setMessage(Component.literal(label + ": " + (newValue ? "Вкл" : "Выкл")));
						})
				.bounds(x, y, 235, 20)
				.build());
	}

	private void addFloatCycle(int x, int y, String label, float[] options, Supplier<Float> getter, Consumer<Float> setter) {
		this.addRenderableWidget(Button.builder(
						Component.literal(label + ": " + formatScale(getter.get())),
						btn -> {
							float current = getter.get();
							float next = options[0];
							for (int i = 0; i < options.length; i++) {
								if (Math.abs(options[i] - current) < 0.001f) {
									next = options[(i + 1) % options.length];
									break;
								}
							}
							setter.accept(next);
							btn.setMessage(Component.literal(label + ": " + formatScale(next)));
						})
				.bounds(x, y, 235, 20)
				.build());
	}

	private void addStringCycle(int x, int y, String label, String[] options, java.util.function.Function<String, String> displayName,
								 Supplier<String> getter, Consumer<String> setter) {
		this.addRenderableWidget(Button.builder(
						Component.literal(label + ": " + displayName.apply(getter.get())),
						btn -> {
							String current = getter.get();
							String next = options[0];
							for (int i = 0; i < options.length; i++) {
								if (options[i].equals(current)) {
									next = options[(i + 1) % options.length];
									break;
								}
							}
							setter.accept(next);
							btn.setMessage(Component.literal(label + ": " + displayName.apply(next)));
						})
				.bounds(x, y, 235, 20)
				.build());
	}

	private static String formatScale(float scale) {
		return scale + "x";
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		int centerX = this.width / 2;
		int topY = this.height / 2 - 130;
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
