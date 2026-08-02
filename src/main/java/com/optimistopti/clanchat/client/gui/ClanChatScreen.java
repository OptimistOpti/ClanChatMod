package com.optimistopti.clanchat.client.gui;

import com.optimistopti.clanchat.chat.Attachment;
import com.optimistopti.clanchat.chat.ChatChannelType;
import com.optimistopti.clanchat.clan.Clan;
import com.optimistopti.clanchat.clan.ClanMember;
import com.optimistopti.clanchat.client.ClanChatModClient;
import com.optimistopti.clanchat.client.ClientClanState;
import com.optimistopti.clanchat.client.config.ClanChatConfig;
import com.optimistopti.clanchat.client.config.ClanChatConfigScreen;
import com.optimistopti.clanchat.client.gui.widget.MessageListWidget;
import com.optimistopti.clanchat.network.ClanAction;
import com.optimistopti.clanchat.network.dto.AcceptDeclineC2S;
import com.optimistopti.clanchat.network.dto.SendMessageC2S;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

/**
 * Единый экран клана: слева каналы, справа переписка + строка ввода с быстрыми вложениями.
 * Открывается по умолчанию клавишей C (см. {@link ClanChatModClient}).
 */
public class ClanChatScreen extends Screen {

	private static final int SIDEBAR_WIDTH = 110;
	private static final int INPUT_HEIGHT = 20;

	private final Screen parent;
	private ChatChannelType channel;
	private UUID whisperTarget;

	private EditBox inputBox;
	private MessageListWidget messageList;
	private int lastSeenStateVersion = -1;

	public ClanChatScreen(Screen parent) {
		this(parent, ChatChannelType.CLAN, null);
	}

	public ClanChatScreen(Screen parent, ChatChannelType channel, UUID whisperTarget) {
		super(Component.literal("ClanChat"));
		this.parent = parent;
		this.channel = channel;
		this.whisperTarget = whisperTarget;
	}

	@Override
	protected void init() {
		lastSeenStateVersion = ClientClanState.INSTANCE.getStateVersion();

		Clan clan = ClientClanState.INSTANCE.getClan();
		if (clan == null) {
			initNoClanView();
			return;
		}

		int panelX = this.width / 2 - 220;
		int panelY = this.height / 2 - 130;
		int panelWidth = 440;
		int panelHeight = 260;

		// --- Sidebar: каналы ---
		int sidebarY = panelY;
		this.addRenderableWidget(channelButton("Клан", ChatChannelType.CLAN, panelX, sidebarY));
		sidebarY += 22;
		this.addRenderableWidget(channelButton("Офицеры", ChatChannelType.OFFICERS, panelX, sidebarY));
		sidebarY += 22;
		this.addRenderableWidget(channelButton("Личные", ChatChannelType.WHISPER, panelX, sidebarY));
		sidebarY += 30;

		this.addRenderableWidget(Button.builder(Component.literal("Участники"), b ->
						this.minecraft.setScreen(new ClanManageScreen(this)))
				.bounds(panelX, sidebarY, SIDEBAR_WIDTH, 20).build());
		sidebarY += 22;
		this.addRenderableWidget(Button.builder(Component.literal("Настройки"), b ->
						this.minecraft.setScreen(new ClanChatConfigScreen(this)))
				.bounds(panelX, sidebarY, SIDEBAR_WIDTH, 20).build());

		// --- Message list ---
		int listX = panelX + SIDEBAR_WIDTH + 10;
		int listY = panelY;
		int listWidth = panelWidth - SIDEBAR_WIDTH - 10;
		int listHeight = panelHeight - INPUT_HEIGHT - 30;

		messageList = new MessageListWidget(listX, listY, listWidth, listHeight, this.font, this::currentMessages);
		this.addRenderableWidget(messageList);

		// --- Attachment quick-buttons ---
		int attachY = listY + listHeight + 4;
		int attachButtonWidth = listWidth / 5 - 2;
		addAttachmentButtons(listX, attachY, attachButtonWidth);

		// --- Input row ---
		int inputY = attachY + 20 + 4;
		inputBox = new EditBox(this.font, listX, inputY, listWidth - 70, INPUT_HEIGHT, Component.literal("Сообщение"));
		inputBox.setMaxLength(512);
		this.addRenderableWidget(inputBox);
		this.setInitialFocus(inputBox);

		this.addRenderableWidget(Button.builder(Component.literal("Отправить"), b -> sendCurrentMessage())
				.bounds(listX + listWidth - 65, inputY, 65, INPUT_HEIGHT).build());
	}

	private void addAttachmentButtons(int listX, int attachY, int buttonWidth) {
		String[] labels = {"Коорд.", "Предмет", "Инвент.", "Сундук", "HP"};
		Runnable[] actions = {
				() -> sendWithAttachment(AttachmentBuilder.coordinates("Текущая позиция")),
				() -> sendWithAttachment(AttachmentBuilder.heldItem()),
				() -> sendWithAttachment(AttachmentBuilder.inventory()),
				() -> sendWithAttachment(AttachmentBuilder.enderChest()),
				() -> sendWithAttachment(AttachmentBuilder.healthStatus())
		};
		for (int i = 0; i < labels.length; i++) {
			final Runnable action = actions[i];
			this.addRenderableWidget(Button.builder(Component.literal(labels[i]), b -> action.run())
					.bounds(listX + i * (buttonWidth + 2), attachY, buttonWidth, 20).build());
		}
	}

	@Override
	public void tick() {
		super.tick();
		int currentVersion = ClientClanState.INSTANCE.getStateVersion();
		if (currentVersion != lastSeenStateVersion) {
			String preservedInput = inputBox != null ? inputBox.getValue() : null;
			this.clearWidgets();
			this.init();
			if (preservedInput != null && inputBox != null) {
				inputBox.setValue(preservedInput);
			}
		}
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (ClanChatConfig.INSTANCE.sendOnEnter && inputBox != null && inputBox.isFocused()
				&& (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
			sendCurrentMessage();
			return true;
		}
		return super.keyPressed(event);
	}

	private void initNoClanView() {
		int centerX = this.width / 2;
		int y = this.height / 2 - 60;

		if (!ClientClanState.INSTANCE.getSystemNotices().isEmpty()) {
			y += 18; // место под последнее системное уведомление, рисуется в extractRenderState
		}

		var invite = ClientClanState.INSTANCE.getPendingInvite();
		if (invite != null) {
			this.addRenderableWidget(Button.builder(
							Component.literal("Принять приглашение в '" + invite.clanName + "' от " + invite.inviterName),
							b -> {
								ClanChatModClient.sendToServer(ClanAction.ACCEPT_INVITE, new AcceptDeclineC2S());
								ClientClanState.INSTANCE.clearPendingInvite();
							})
					.bounds(centerX - 150, y, 300, 20).build());
			y += 24;
			this.addRenderableWidget(Button.builder(Component.literal("Отклонить"), b -> {
						ClanChatModClient.sendToServer(ClanAction.DECLINE_INVITE, new AcceptDeclineC2S());
						ClientClanState.INSTANCE.clearPendingInvite();
						this.minecraft.setScreen(new ClanChatScreen(parent));
					})
					.bounds(centerX - 100, y, 200, 20).build());
			y += 34;
		}

		this.addRenderableWidget(Button.builder(Component.literal("Создать клан"), b ->
						this.minecraft.setScreen(new ClanCreateScreen(this)))
				.bounds(centerX - 100, y, 200, 20).build());
		y += 24;
		this.addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> this.onClose())
				.bounds(centerX - 100, y, 200, 20).build());
	}

	private Button channelButton(String label, ChatChannelType type, int x, int y) {
		String suffix = this.channel == type ? " ◀" : "";
		return Button.builder(Component.literal(label + suffix), b -> {
					this.channel = type;
					this.minecraft.setScreen(new ClanChatScreen(parent, type, whisperTarget));
				})
				.bounds(x, y, SIDEBAR_WIDTH, 20).build();
	}

	private java.util.List<com.optimistopti.clanchat.chat.ChatMessage> currentMessages() {
		if (channel == ChatChannelType.WHISPER && whisperTarget != null) {
			return ClientClanState.INSTANCE.getWhisperThread(whisperTarget);
		}
		return ClientClanState.INSTANCE.getMessages(channel);
	}

	private void sendCurrentMessage() {
		if (inputBox == null) {
			return;
		}
		String text = inputBox.getValue();
		if (text.isBlank()) {
			return;
		}
		doSend(text, null);
		inputBox.setValue("");
	}

	private void sendWithAttachment(Attachment attachment) {
		if (attachment == null) {
			return;
		}
		String text = inputBox != null ? inputBox.getValue() : "";
		doSend(text, attachment);
		if (inputBox != null) {
			inputBox.setValue("");
		}
	}

	private void doSend(String text, Attachment attachment) {
		SendMessageC2S dto = new SendMessageC2S();
		dto.channel = channel.name();
		dto.content = text;
		if (channel == ChatChannelType.WHISPER && whisperTarget != null) {
			dto.whisperTargetUuid = whisperTarget.toString();
		}
		if (attachment != null) {
			dto.attachmentType = attachment.type().name();
			dto.attachmentDataJson = attachment.dataJson();
		}
		ClanChatModClient.sendToServer(ClanAction.SEND_MESSAGE, dto);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		Clan clan = ClientClanState.INSTANCE.getClan();
		String title = clan != null ? clan.getName() + " [" + clan.getTag() + "]" : "ClanChat";
		graphics.text(this.font, title, this.width / 2 - 220, this.height / 2 - 145, 0xFFFFFFFF, true);

		if (clan == null && !ClientClanState.INSTANCE.getSystemNotices().isEmpty()) {
			var notices = ClientClanState.INSTANCE.getSystemNotices();
			var last = notices.get(notices.size() - 1);
			int color = "error".equals(last.level) ? 0xFFFF5555 : 0xFF55FF55;
			graphics.text(this.font, last.text, this.width / 2 - 150, this.height / 2 - 78, color, false);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}
}
