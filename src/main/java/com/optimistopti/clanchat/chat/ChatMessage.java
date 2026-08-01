package com.optimistopti.clanchat.chat;

import java.util.UUID;

public record ChatMessage(
		UUID id,
		ChatChannelType channel,
		UUID clanId,
		UUID senderUuid,
		String senderName,
		int senderColor,
		/** Заполнено только для {@link ChatChannelType#WHISPER}. */
		UUID whisperTargetUuid,
		String whisperTargetName,
		String content,
		long timestampEpochMillis,
		/** Может быть {@code null}, если сообщение без вложения. */
		Attachment attachment
) {
}
