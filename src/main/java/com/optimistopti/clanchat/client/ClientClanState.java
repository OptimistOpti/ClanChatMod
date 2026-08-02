package com.optimistopti.clanchat.client;

import com.optimistopti.clanchat.chat.ChatChannelType;
import com.optimistopti.clanchat.chat.ChatMessage;
import com.optimistopti.clanchat.clan.Clan;
import com.optimistopti.clanchat.network.dto.InviteReceivedS2C;
import com.optimistopti.clanchat.network.dto.SystemNoticeS2C;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Единственный источник правды на клиенте о состоянии клана игрока и чата.
 * Заполняется исключительно из S2C-пакетов, GUI только читает.
 * <p>
 * {@link #getStateVersion()} растёт при любом изменении — экраны сверяют его в своём
 * {@code tick()} и перестраивают виджеты, если он вырос с последней отрисовки.
 * Без этого, например, экран создания клана после ответа сервера так и продолжал бы
 * показывать "клана ещё нет", пока игрок не закроет и не откроет чат заново.
 */
public final class ClientClanState {

	public static final ClientClanState INSTANCE = new ClientClanState();

	private volatile Clan clan;
	private final Map<ChatChannelType, List<ChatMessage>> messagesByChannel = new EnumMap<>(ChatChannelType.class);
	private InviteReceivedS2C pendingInvite;
	private final List<SystemNoticeS2C> systemNotices = new ArrayList<>();
	/** Личка группируется отдельно от прочих каналов, по собеседнику. */
	private final Map<UUID, List<ChatMessage>> whisperThreads = new java.util.HashMap<>();
	private final AtomicInteger stateVersion = new AtomicInteger();

	private ClientClanState() {
		for (ChatChannelType type : ChatChannelType.values()) {
			messagesByChannel.put(type, new ArrayList<>());
		}
	}

	public int getStateVersion() {
		return stateVersion.get();
	}

	public Clan getClan() {
		return clan;
	}

	public void setClan(Clan clan) {
		this.clan = clan;
		if (clan == null) {
			clearMessages();
		}
		stateVersion.incrementAndGet();
	}

	public boolean hasClan() {
		return clan != null;
	}

	public void clearMessages() {
		messagesByChannel.values().forEach(List::clear);
		whisperThreads.clear();
	}

	public void setHistory(List<ChatMessage> history) {
		clearMessages();
		for (ChatMessage message : history) {
			addMessage(message);
		}
	}

	public void addMessage(ChatMessage message) {
		if (message.channel() == ChatChannelType.WHISPER) {
			UUID selfUuid = net.minecraft.client.Minecraft.getInstance().player != null
					? net.minecraft.client.Minecraft.getInstance().player.getUUID() : null;
			UUID otherParty = message.senderUuid().equals(selfUuid) ? message.whisperTargetUuid() : message.senderUuid();
			whisperThreads.computeIfAbsent(otherParty, u -> new ArrayList<>()).add(message);
		} else {
			messagesByChannel.get(message.channel()).add(message);
		}
		// Намеренно НЕ трогаем stateVersion: MessageListWidget читает сообщения "вживую"
		// через Supplier при каждой отрисовке, полная пересборка экрана тут не нужна и
		// сбрасывала бы текст, который игрок в этот момент печатает.
	}

	public List<ChatMessage> getMessages(ChatChannelType channel) {
		return messagesByChannel.getOrDefault(channel, List.of());
	}

	public List<ChatMessage> getWhisperThread(UUID otherParty) {
		return whisperThreads.getOrDefault(otherParty, List.of());
	}

	public Map<UUID, List<ChatMessage>> getWhisperThreads() {
		return whisperThreads;
	}

	public InviteReceivedS2C getPendingInvite() {
		return pendingInvite;
	}

	public void setPendingInvite(InviteReceivedS2C invite) {
		this.pendingInvite = invite;
		stateVersion.incrementAndGet();
	}

	public void clearPendingInvite() {
		this.pendingInvite = null;
		stateVersion.incrementAndGet();
	}

	public List<SystemNoticeS2C> getSystemNotices() {
		return systemNotices;
	}

	public void pushSystemNotice(SystemNoticeS2C notice) {
		systemNotices.add(notice);
		while (systemNotices.size() > 50) {
			systemNotices.remove(0);
		}
		stateVersion.incrementAndGet();
	}
}
