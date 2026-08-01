package com.optimistopti.clanchat.chat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Хранит последние N сообщений на клан в памяти сервера, чтобы при открытии GUI
 * (или заходе на сервер) клиенту можно было прислать "хвост" истории.
 * <p>
 * TODO(follow-up issue): персистить историю на диск (сейчас обнуляется при рестарте сервера).
 */
public class ClanChatHistory {

	private static final int MAX_MESSAGES_PER_CLAN = 200;

	private final Map<UUID, Deque<ChatMessage>> byClan = new HashMap<>();

	public void add(ChatMessage message) {
		if (message.clanId() == null) {
			return;
		}
		Deque<ChatMessage> deque = byClan.computeIfAbsent(message.clanId(), id -> new ArrayDeque<>());
		deque.addLast(message);
		while (deque.size() > MAX_MESSAGES_PER_CLAN) {
			deque.removeFirst();
		}
	}

	public List<ChatMessage> getRecent(UUID clanId) {
		Deque<ChatMessage> deque = byClan.get(clanId);
		return deque == null ? List.of() : List.copyOf(deque);
	}

	public void clear(UUID clanId) {
		byClan.remove(clanId);
	}
}
