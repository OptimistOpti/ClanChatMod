package com.optimistopti.clanchat.client.gui;

import com.optimistopti.clanchat.clan.Clan;
import com.optimistopti.clanchat.clan.ClanMember;
import com.optimistopti.clanchat.clan.ClanRole;
import com.optimistopti.clanchat.client.ClanChatModClient;
import com.optimistopti.clanchat.client.ClientClanState;
import com.optimistopti.clanchat.network.ClanAction;
import com.optimistopti.clanchat.network.dto.AcceptDeclineC2S;
import com.optimistopti.clanchat.network.dto.InviteC2S;
import com.optimistopti.clanchat.network.dto.SetRoleC2S;
import com.optimistopti.clanchat.network.dto.TargetUuidC2S;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Список участников клана + приглашение по нику + кик/повышение/понижение.
 * Сервер сам отклонит действие системным уведомлением, если у игрока нет прав —
 * экран специально не дублирует проверку прав на клиенте, чтобы не рассинхронизироваться.
 */
public class ClanManageScreen extends Screen {

	private final Screen parent;
	private EditBox inviteBox;
	private EditBox homeNameBox;
	private int lastSeenStateVersion = -1;

	public ClanManageScreen(Screen parent) {
		super(Component.literal("Участники клана"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		lastSeenStateVersion = ClientClanState.INSTANCE.getStateVersion();
		Clan clan = ClientClanState.INSTANCE.getClan();
		int centerX = this.width / 2;
		int y = this.height / 2 - 110;

		if (clan == null) {
			this.addRenderableWidget(Button.builder(Component.literal("Назад"), b -> this.onClose())
					.bounds(centerX - 60, y, 120, 20).build());
			return;
		}

		List<ClanMember> members = new ArrayList<>(clan.getMembers().values());
		members.sort((a, b) -> Integer.compare(b.getRole().getRank(), a.getRole().getRank()));

		var self = net.minecraft.client.Minecraft.getInstance().player;
		java.util.UUID selfUuid = self != null ? self.getUUID() : null;

		for (ClanMember member : members) {
			String label = member.getLastKnownName() + " — " + roleLabel(member.getRole());
			this.addRenderableWidget(Button.builder(Component.literal(label), b -> {})
					.bounds(centerX - 180, y, 170, 20).build());

			boolean isSelf = member.getUuid().equals(selfUuid);
			if (!isSelf) {
				this.addRenderableWidget(Button.builder(Component.literal("\u2709"), b ->
								this.minecraft.setScreen(new ClanChatScreen(this, com.optimistopti.clanchat.chat.ChatChannelType.WHISPER, member.getUuid())))
						.bounds(centerX - 6, y, 20, 20).build());
			}

			if (member.getRole() != ClanRole.LEADER) {
				this.addRenderableWidget(Button.builder(Component.literal("Кик"), b -> kick(member))
						.bounds(centerX + 18, y, 40, 20).build());
				this.addRenderableWidget(Button.builder(
								Component.literal(member.getRole() == ClanRole.OFFICER ? "-> Участник" : "-> Заместитель"),
								b -> promoteDemote(member))
						.bounds(centerX + 62, y, 96, 20).build());
			}
			y += 22;
		}

		y += 6;
		inviteBox = new EditBox(this.font, centerX - 180, y, 150, 20, Component.literal("Ник игрока"));
		this.addRenderableWidget(inviteBox);
		this.addRenderableWidget(Button.builder(Component.literal("Пригласить"), b -> invite())
				.bounds(centerX - 24, y, 90, 20).build());
		y += 30;

		this.addRenderableWidget(Button.builder(Component.literal("— Точки клана —"), b -> {})
				.bounds(centerX - 180, y, 360, 16).build());
		y += 20;

		var homes = new ArrayList<>(clan.getHomes().values());
		homes.sort(java.util.Comparator.comparing(com.optimistopti.clanchat.clan.ClanHome::name));
		for (var home : homes) {
			String label = home.name() + " (" + Math.round(home.x()) + ", " + Math.round(home.y()) + ", " + Math.round(home.z()) + ")";
			this.addRenderableWidget(Button.builder(Component.literal(label), b -> {})
					.bounds(centerX - 180, y, 200, 20).build());
			this.addRenderableWidget(Button.builder(Component.literal("Отправить"), b -> sendHomeToChat(home))
					.bounds(centerX + 24, y, 80, 20).build());
			this.addRenderableWidget(Button.builder(Component.literal("X"), b -> deleteHome(home.name()))
					.bounds(centerX + 108, y, 20, 20).build());
			y += 22;
		}

		homeNameBox = new EditBox(this.font, centerX - 180, y, 150, 20, Component.literal("Название точки"));
		this.addRenderableWidget(homeNameBox);
		this.addRenderableWidget(Button.builder(Component.literal("Добавить (тут)"), b -> addHomeHere())
				.bounds(centerX - 24, y, 110, 20).build());
		y += 26;

		this.addRenderableWidget(Button.builder(Component.literal("Покинуть клан"), b -> {
					ClanChatModClient.sendToServer(ClanAction.LEAVE, new AcceptDeclineC2S());
					this.minecraft.setScreen(parent);
				})
				.bounds(centerX - 180, y, 120, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Распустить клан"), b -> {
					ClanChatModClient.sendToServer(ClanAction.DISBAND, new AcceptDeclineC2S());
					this.minecraft.setScreen(parent);
				})
				.bounds(centerX - 50, y, 120, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Назад"), b -> this.onClose())
				.bounds(centerX + 80, y, 100, 20).build());
	}

	private static String roleLabel(ClanRole role) {
		return switch (role) {
			case LEADER -> "Лидер";
			case OFFICER -> "Заместитель";
			case MEMBER -> "Участник";
		};
	}

	private void kick(ClanMember member) {
		TargetUuidC2S dto = new TargetUuidC2S();
		dto.targetUuid = member.getUuid().toString();
		ClanChatModClient.sendToServer(ClanAction.KICK, dto);
		// Экран сам перестроится в tick(), как только придёт обновлённый CLAN_STATE —
		// не пересоздаём его тут же со старыми данными.
	}

	private void promoteDemote(ClanMember member) {
		SetRoleC2S dto = new SetRoleC2S();
		dto.targetUuid = member.getUuid().toString();
		dto.role = member.getRole() == ClanRole.OFFICER ? ClanRole.MEMBER.name() : ClanRole.OFFICER.name();
		ClanChatModClient.sendToServer(ClanAction.SET_ROLE, dto);
	}

	private void invite() {
		if (inviteBox == null || inviteBox.getValue().isBlank()) {
			return;
		}
		InviteC2S dto = new InviteC2S();
		dto.targetName = inviteBox.getValue().trim();
		ClanChatModClient.sendToServer(ClanAction.INVITE, dto);
		inviteBox.setValue("");
	}

	private void sendHomeToChat(com.optimistopti.clanchat.clan.ClanHome home) {
		var dto = new com.optimistopti.clanchat.network.dto.SendMessageC2S();
		dto.channel = com.optimistopti.clanchat.chat.ChatChannelType.CLAN.name();
		dto.content = "";
		dto.attachmentType = com.optimistopti.clanchat.chat.AttachmentType.COORDINATES.name();
		var data = new com.optimistopti.clanchat.chat.Attachment.CoordinatesData(
				home.name(), home.dimensionId(), home.x(), home.y(), home.z());
		dto.attachmentDataJson = com.optimistopti.clanchat.clan.ClanGson.INSTANCE.toJson(data);
		ClanChatModClient.sendToServer(ClanAction.SEND_MESSAGE, dto);
	}

	private void deleteHome(String name) {
		var dto = new com.optimistopti.clanchat.network.dto.HomeNameC2S();
		dto.name = name;
		ClanChatModClient.sendToServer(ClanAction.DELETE_HOME, dto);
	}

	private void addHomeHere() {
		if (homeNameBox == null || homeNameBox.getValue().isBlank()) {
			return;
		}
		var player = net.minecraft.client.Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		var dto = new com.optimistopti.clanchat.network.dto.SetHomeC2S();
		dto.name = homeNameBox.getValue().trim();
		dto.dimensionId = player.level().dimension().identifier().toString();
		dto.x = player.getX();
		dto.y = player.getY();
		dto.z = player.getZ();
		ClanChatModClient.sendToServer(ClanAction.SET_HOME, dto);
		homeNameBox.setValue("");
	}

	@Override
	public void tick() {
		super.tick();
		int currentVersion = ClientClanState.INSTANCE.getStateVersion();
		if (currentVersion != lastSeenStateVersion) {
			String preservedInvite = inviteBox != null ? inviteBox.getValue() : null;
			String preservedHomeName = homeNameBox != null ? homeNameBox.getValue() : null;
			this.clearWidgets();
			this.init();
			if (preservedInvite != null && inviteBox != null) {
				inviteBox.setValue(preservedInvite);
			}
			if (preservedHomeName != null && homeNameBox != null) {
				homeNameBox.setValue(preservedHomeName);
			}
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.text(this.font, "Участники клана", this.width / 2 - 180, this.height / 2 - 125, 0xFFFFFFFF, true);
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
