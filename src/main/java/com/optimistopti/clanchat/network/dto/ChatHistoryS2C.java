package com.optimistopti.clanchat.network.dto;

import com.optimistopti.clanchat.chat.ChatMessage;

import java.util.List;

public class ChatHistoryS2C {
	public List<ChatMessage> messages;

	public ChatHistoryS2C(List<ChatMessage> messages) {
		this.messages = messages;
	}
}
