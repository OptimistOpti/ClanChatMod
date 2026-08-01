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

/**
 * Единственный источник правды на клиенте о состоянии клана игрока и чата.
 * Заполняется исключительно из S2C-пакетов, GUI только читает.
 */
public final class ClientClanState {

	public static final ClientClanState INSTANCE = new ClientClanState();

	private volatile Clan clan;
	private final Map<ChatChannelType, List<ChatMessage>> messagesByChannel = new EnumMap<>(ChatChannelType.class);
	private InviteReceivedS2C pendingInvite;
	private final List<SystemNoticeS2C> systemNotices = new ArrayList<>();
	/** Личка группируется отдельно от прочих каналов, по собеседнику. */
	private final Map<UUID, List<ChatMessage>> whisperThreads = new java.util.HashMap<>();

	private ClientClanState() {
		for (ChatChannelType type : ChatChannelType.values()) {
			messagesByChannel.put(type, new ArrayList<>());
		}
	}

	public Clan getClan() {
		return clan;
	}

	public void setClan(Clan clan) {
		this.clan = clan;
		if (clan == null) {
			clearMessages();
		}
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
	}

	public void clearPendingInvite() {
		this.pendingInvite = null;
	}

	public List<SystemNoticeS2C> getSystemNotices() {
		return systemNotices;
	}

	public void pushSystemNotice(SystemNoticeS2C notice) {
		systemNotices.add(notice);
		while (systemNotices.size() > 50) {
			systemNotices.remove(0);
		}
	}
}
