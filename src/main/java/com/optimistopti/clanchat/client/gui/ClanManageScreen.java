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

		for (ClanMember member : members) {
			String label = member.getLastKnownName() + " — " + member.getRole().name();
			this.addRenderableWidget(Button.builder(Component.literal(label), b -> {})
					.bounds(centerX - 180, y, 200, 20).build());

			if (member.getRole() != ClanRole.LEADER) {
				this.addRenderableWidget(Button.builder(Component.literal("Кик"), b -> kick(member))
						.bounds(centerX + 24, y, 40, 20).build());
				this.addRenderableWidget(Button.builder(
								Component.literal(member.getRole() == ClanRole.OFFICER ? "-> Участник" : "-> Офицер"),
								b -> promoteDemote(member))
						.bounds(centerX + 68, y, 90, 20).build());
			}
			y += 22;
		}

		y += 6;
		inviteBox = new EditBox(this.font, centerX - 180, y, 150, 20, Component.literal("Ник игрока"));
		this.addRenderableWidget(inviteBox);
		this.addRenderableWidget(Button.builder(Component.literal("Пригласить"), b -> invite())
				.bounds(centerX - 24, y, 90, 20).build());
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

	@Override
	public void tick() {
		super.tick();
		int currentVersion = ClientClanState.INSTANCE.getStateVersion();
		if (currentVersion != lastSeenStateVersion) {
			String preservedInvite = inviteBox != null ? inviteBox.getValue() : null;
			this.clearWidgets();
			this.init();
			if (preservedInvite != null && inviteBox != null) {
				inviteBox.setValue(preservedInvite);
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
