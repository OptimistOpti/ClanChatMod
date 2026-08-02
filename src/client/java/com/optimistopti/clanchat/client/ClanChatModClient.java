package com.optimistopti.clanchat.client;

import com.optimistopti.clanchat.ClanChatMod;
import com.optimistopti.clanchat.chat.ChatMessage;
import com.optimistopti.clanchat.clan.Clan;
import com.optimistopti.clanchat.client.config.ClanChatConfig;
import com.optimistopti.clanchat.client.gui.ClanChatScreen;
import com.optimistopti.clanchat.network.ClanAction;
import com.optimistopti.clanchat.network.Envelope;
import com.optimistopti.clanchat.network.dto.ChatHistoryS2C;
import com.optimistopti.clanchat.network.dto.InviteReceivedS2C;
import com.optimistopti.clanchat.network.dto.SystemNoticeS2C;
import com.optimistopti.clanchat.network.payload.ClanChatC2SPayload;
import com.optimistopti.clanchat.network.payload.ClanChatS2CPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public class ClanChatModClient implements ClientModInitializer {

	public static KeyMapping OPEN_CHAT_KEY;

	@Override
	public void onInitializeClient() {
		ClanChatConfig.load();

		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath(ClanChatMod.MOD_ID, "main")
		);

		OPEN_CHAT_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.clanchat.open_chat",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_C,
				category
		));

		ClientPlayNetworking.registerGlobalReceiver(ClanChatS2CPayload.TYPE, (payload, context) ->
				context.client().execute(() -> handleIncoming(payload.json())));

		// При выходе с сервера (или закрытии одиночной игры) очищаем клиентский кэш,
		// чтобы не потащить данные предыдущего сервера на следующий.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientClanState.INSTANCE.setClan(null);
			ClientClanState.INSTANCE.clearPendingInvite();
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_CHAT_KEY.consumeClick()) {
				if (client.player != null && client.screen == null) {
					client.setScreen(new ClanChatScreen(null));
				}
			}
		});
	}

	private static void handleIncoming(String json) {
		Envelope envelope;
		try {
			envelope = Envelope.fromJson(json);
		} catch (Exception e) {
			ClanChatMod.LOGGER.warn("Некорректный пакет ClanChat от сервера: {}", e.toString());
			return;
		}

		switch (envelope.actionEnum()) {
			case CLAN_STATE -> ClientClanState.INSTANCE.setClan(envelope.dataAs(Clan.class));
			case NO_CLAN -> ClientClanState.INSTANCE.setClan(null);
			case CHAT_MESSAGE -> {
				ChatMessage message = envelope.dataAs(ChatMessage.class);
				ClientClanState.INSTANCE.addMessage(message);
				maybePlayNotificationSound(message);
			}
			case CHAT_HISTORY -> ClientClanState.INSTANCE.setHistory(envelope.dataAs(ChatHistoryS2C.class).messages);
			case INVITE_RECEIVED -> {
				InviteReceivedS2C invite = envelope.dataAs(InviteReceivedS2C.class);
				ClientClanState.INSTANCE.setPendingInvite(invite);
				Minecraft mc = Minecraft.getInstance();
				if (mc.player != null) {
					mc.player.sendSystemMessage(
							net.minecraft.network.chat.Component.literal(
									"§e[ClanChat] " + invite.inviterName + " приглашает тебя в клан '" + invite.clanName + "'. Открой чат клана (клавиша по умолчанию C), чтобы принять."
							));
				}
			}
			case SYSTEM_NOTICE -> ClientClanState.INSTANCE.pushSystemNotice(envelope.dataAs(SystemNoticeS2C.class));
			default -> ClanChatMod.LOGGER.warn("Неизвестное действие ClanChat (клиент): {}", envelope.action);
		}
	}

	private static void maybePlayNotificationSound(ChatMessage message) {
		if (!ClanChatConfig.INSTANCE.playSoundOnMessage) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || message.senderUuid().equals(mc.player.getUUID())) {
			return; // не пищим на собственные сообщения
		}
		mc.player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 0.4f, 1.6f);
	}

	public static void sendToServer(ClanAction action, Object dataDto) {
		Envelope envelope = new Envelope(action, Envelope.toDataObject(dataDto));
		ClientPlayNetworking.send(new ClanChatC2SPayload(envelope.toJson()));
	}
}
