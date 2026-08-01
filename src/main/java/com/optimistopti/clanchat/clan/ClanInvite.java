package com.optimistopti.clanchat.clan;

import java.util.UUID;

/**
 * Активное приглашение в клан. Хранится только в памяти сервера (не персистится),
 * протухает через {@link #EXPIRY_MILLIS}.
 */
public record ClanInvite(UUID clanId, String clanName, UUID inviterUuid, String inviterName,
						  UUID targetUuid, long createdAtEpochMillis) {

	public static final long EXPIRY_MILLIS = 5 * 60 * 1000L; // 5 минут

	public boolean isExpired(long nowEpochMillis) {
		return nowEpochMillis - createdAtEpochMillis > EXPIRY_MILLIS;
	}
}
