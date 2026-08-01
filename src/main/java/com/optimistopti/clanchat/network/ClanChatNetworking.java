package com.optimistopti.clanchat.network;

import com.optimistopti.clanchat.ClanChatMod;
import com.optimistopti.clanchat.chat.AttachmentType;
import com.optimistopti.clanchat.chat.Attachment;
import com.optimistopti.clanchat.chat.ChatChannelType;
import com.optimistopti.clanchat.chat.ChatMessage;
import com.optimistopti.clanchat.clan.Clan;
import com.optimistopti.clanchat.clan.ClanActionException;
import com.optimistopti.clanchat.clan.ClanInvite;
import com.optimistopti.clanchat.clan.ClanManager;
import com.optimistopti.clanchat.clan.ClanMember;
import com.optimistopti.clanchat.clan.ClanPermission;
import com.optimistopti.clanchat.clan.ClanRole;
import com.optimistopti.clanchat.network.dto.ChatHistoryS2C;
import com.optimistopti.clanchat.network.dto.CreateClanC2S;
import com.optimistopti.clanchat.network.dto.InviteC2S;
import com.optimistopti.clanchat.network.dto.InviteReceivedS2C;
import com.optimistopti.clanchat.network.dto.SendMessageC2S;
import com.optimistopti.clanchat.network.dto.SetRoleC2S;
import com.optimistopti.clanchat.network.dto.SystemNoticeS2C;
import com.optimistopti.clanchat.network.dto.TargetUuidC2S;
import com.optimistopti.clanchat.network.payload.ClanChatC2SPayload;
import com.optimistopti.clanchat.network.payload.ClanChatS2CPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/**
 * Регистрация сетевых пейлоадов и обработка входящих C2S-действий на сервере.
 * Отправка S2C-пакетов централизована в {@code send*} методах, чтобы вся рассылка
 * (кому конкретно уходит пакет) была в одном месте.
 */
public final class ClanChatNetworking {

	private static final int MAX_MESSAGE_LENGTH = 512;
	private static final int MAX_ATTACHMENT_JSON_LENGTH = 8192;

	private ClanChatNetworking() {
	}

	public static void registerPayloadTypes() {
		PayloadTypeRegistry.serverboundPlay().register(ClanChatC2SPayload.TYPE, ClanChatC2SPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ClanChatS2CPayload.TYPE, ClanChatS2CPayload.CODEC);
	}

	public static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(ClanChatC2SPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			player.server.execute(() -> handleIncoming(player, payload.json()));
		});

		// Как только игрок зашёл на сервер (или в одиночную игру) — присылаем ему актуальное
		// состояние клана (или NO_CLAN) и хвост истории чата.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			ClanManager manager = ClanChatMod.getClanManager();
			if (manager != null) {
				manager.updateLastKnownName(player);
				sendFullState(player, manager);
			}
		});
	}

	// ---------------------------------------------------------------- dispatch

	private static void handleIncoming(ServerPlayer player, String json) {
		ClanManager manager = ClanChatMod.getClanManager();
		if (manager == null) {
			return;
		}
		Envelope envelope;
		try {
			envelope = Envelope.fromJson(json);
		} catch (Exception e) {
			ClanChatMod.LOGGER.warn("Некорректный пакет ClanChat от {}: {}", player.getGameProfile().getName(), e.toString());
			return;
		}

		try {
			switch (envelope.actionEnum()) {
				case SEND_MESSAGE -> handleSendMessage(player, manager, envelope.dataAs(SendMessageC2S.class));
				case CREATE_CLAN -> handleCreateClan(player, manager, envelope.dataAs(CreateClanC2S.class));
				case INVITE -> handleInvite(player, manager, envelope.dataAs(InviteC2S.class));
				case ACCEPT_INVITE -> handleAcceptInvite(player, manager);
				case DECLINE_INVITE -> {
					manager.declineInvite(player);
				}
				case KICK -> handleKick(player, manager, envelope.dataAs(TargetUuidC2S.class));
				case SET_ROLE -> handleSetRole(player, manager, envelope.dataAs(SetRoleC2S.class));
				case LEAVE -> {
					manager.leave(player);
					sendFullState(player, manager);
				}
				case DISBAND -> {
					Clan clan = manager.getClanOf(player.getUUID());
					manager.disbandClan(player);
					if (clan != null) {
						broadcastStateToClanMembers(clan, manager, player.server);
					}
				}
				case REQUEST_STATE -> sendFullState(player, manager);
				default -> ClanChatMod.LOGGER.warn("Неизвестное действие ClanChat: {}", envelope.action);
			}
		} catch (ClanActionException e) {
			sendSystemNotice(player, e.getMessage(), "error");
		} catch (Exception e) {
			ClanChatMod.LOGGER.error("Ошибка обработки действия ClanChat {} от {}", envelope.action, player.getGameProfile().getName(), e);
			sendSystemNotice(player, "Внутренняя ошибка, попробуй ещё раз.", "error");
		}
	}

	private static void handleSendMessage(ServerPlayer player, ClanManager manager, SendMessageC2S data) throws ClanActionException {
		Clan clan = manager.getClanOf(player.getUUID());
		if (clan == null) {
			throw new ClanActionException("Ты не состоишь в клане.");
		}
		ChatChannelType channel = ChatChannelType.valueOf(data.channel);
		if (channel == ChatChannelType.OFFICERS && !clan.hasPermission(player.getUUID(), ClanPermission.SEND_OFFICER_CHAT)
				&& clan.getMembers().get(player.getUUID()).getRole() != ClanRole.LEADER) {
			throw new ClanActionException("У тебя нет доступа к офицерскому чату.");
		}
		if (channel == ChatChannelType.SYSTEM) {
			throw new ClanActionException("Нельзя отправлять сообщения в системный канал.");
		}

		String content = data.content == null ? "" : data.content.trim();
		if (content.isEmpty() && data.attachmentType == null) {
			return;
		}
		if (content.length() > MAX_MESSAGE_LENGTH) {
			content = content.substring(0, MAX_MESSAGE_LENGTH);
		}

		Attachment attachment = null;
		if (data.attachmentType != null && data.attachmentDataJson != null
				&& data.attachmentDataJson.length() <= MAX_ATTACHMENT_JSON_LENGTH) {
			attachment = new Attachment(AttachmentType.valueOf(data.attachmentType), data.attachmentDataJson);
		}

		UUID whisperTarget = null;
		String whisperTargetName = null;
		if (channel == ChatChannelType.WHISPER && data.whisperTargetUuid != null) {
			whisperTarget = UUID.fromString(data.whisperTargetUuid);
			ClanMember targetMember = clan.getMembers().get(whisperTarget);
			if (targetMember == null) {
				throw new ClanActionException("Этот игрок не в твоём клане.");
			}
			whisperTargetName = targetMember.getLastKnownName();
		}

		ClanMember sender = clan.getMembers().get(player.getUUID());
		ChatMessage message = new ChatMessage(
				UUID.randomUUID(),
				channel,
				clan.getId(),
				player.getUUID(),
				player.getGameProfile().getName(),
				sender.getRole().getDefaultColor(),
				whisperTarget,
				whisperTargetName,
				content,
				System.currentTimeMillis(),
				attachment
		);

		ClanChatMod.getChatHistory().add(message);

		UUID finalWhisperTarget = whisperTarget;
		for (UUID memberUuid : clan.getMembers().keySet()) {
			if (channel == ChatChannelType.WHISPER
					&& !memberUuid.equals(player.getUUID()) && !memberUuid.equals(finalWhisperTarget)) {
				continue;
			}
			if (channel == ChatChannelType.OFFICERS && !clan.hasPermission(memberUuid, ClanPermission.SEND_OFFICER_CHAT)
					&& clan.getMembers().get(memberUuid).getRole() != ClanRole.LEADER) {
				continue;
			}
			ServerPlayer recipient = player.server.getPlayerList().getPlayer(memberUuid);
			if (recipient != null) {
				sendMessage(recipient, message);
			}
		}
	}

	private static void handleCreateClan(ServerPlayer player, ClanManager manager, CreateClanC2S data) throws ClanActionException {
		Clan clan = manager.createClan(player, data.name, data.tag, data.color);
		sendFullState(player, manager);
	}

	private static void handleInvite(ServerPlayer player, ClanManager manager, InviteC2S data) throws ClanActionException {
		ServerPlayer target = player.server.getPlayerList().getPlayerByName(data.targetName);
		if (target == null) {
			throw new ClanActionException("Игрок с ником '" + data.targetName + "' не найден онлайн.");
		}
		manager.invite(player, target);
		Clan clan = manager.getClanOf(player.getUUID());
		sendToClient(target, ClanAction.INVITE_RECEIVED,
				new InviteReceivedS2C(clan.getName(), player.getGameProfile().getName()));
		sendSystemNotice(player, "Приглашение отправлено игроку " + target.getGameProfile().getName() + ".", "info");
	}

	private static void handleAcceptInvite(ServerPlayer player, ClanManager manager) throws ClanActionException {
		Clan clan = manager.acceptInvite(player);
		broadcastStateToClanMembers(clan, manager, player.server);
	}

	private static void handleKick(ServerPlayer player, ClanManager manager, TargetUuidC2S data) throws ClanActionException {
		Clan clan = manager.getClanOf(player.getUUID());
		UUID targetUuid = UUID.fromString(data.targetUuid);
		manager.kick(player, targetUuid);
		if (clan != null) {
			broadcastStateToClanMembers(clan, manager, player.server);
			ServerPlayer kicked = player.server.getPlayerList().getPlayer(targetUuid);
			if (kicked != null) {
				sendFullState(kicked, manager);
			}
		}
	}

	private static void handleSetRole(ServerPlayer player, ClanManager manager, SetRoleC2S data) throws ClanActionException {
		Clan clan = manager.getClanOf(player.getUUID());
		manager.setRole(player, UUID.fromString(data.targetUuid), ClanRole.valueOf(data.role));
		if (clan != null) {
			broadcastStateToClanMembers(clan, manager, player.server);
		}
	}

	// ---------------------------------------------------------------- sending helpers

	private static void broadcastStateToClanMembers(Clan clan, ClanManager manager, MinecraftServer server) {
		for (UUID memberUuid : clan.getMembers().keySet()) {
			ServerPlayer member = server.getPlayerList().getPlayer(memberUuid);
			if (member != null) {
				sendFullState(member, manager);
			}
		}
	}

	private static void sendFullState(ServerPlayer player, ClanManager manager) {
		Clan clan = manager.getClanOf(player.getUUID());
		if (clan == null) {
			sendToClient(player, ClanAction.NO_CLAN, new Object());
			return;
		}
		sendToClient(player, ClanAction.CLAN_STATE, clan);
		List<ChatMessage> history = ClanChatMod.getChatHistory().getRecent(clan.getId());
		sendToClient(player, ClanAction.CHAT_HISTORY, new ChatHistoryS2C(history));
	}

	private static void sendMessage(ServerPlayer player, ChatMessage message) {
		sendToClient(player, ClanAction.CHAT_MESSAGE, message);
	}

	private static void sendSystemNotice(ServerPlayer player, String text, String level) {
		sendToClient(player, ClanAction.SYSTEM_NOTICE, new SystemNoticeS2C(text, level));
	}

	private static void sendToClient(ServerPlayer player, ClanAction action, Object dataDto) {
		Envelope envelope = new Envelope(action, Envelope.toDataObject(dataDto));
		ServerPlayNetworking.send(player, new ClanChatS2CPayload(envelope.toJson()));
	}
}
