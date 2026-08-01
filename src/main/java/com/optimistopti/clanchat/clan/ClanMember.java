package com.optimistopti.clanchat.clan;

import java.util.UUID;

/**
 * Участник клана. {@code lastKnownName} кэшируется, чтобы не зависеть от того,
 * онлайн игрок или нет, когда нужно отобразить список участников.
 */
public class ClanMember {
	private final UUID uuid;
	private String lastKnownName;
	private ClanRole role;
	private final long joinedAtEpochMillis;

	public ClanMember(UUID uuid, String lastKnownName, ClanRole role, long joinedAtEpochMillis) {
		this.uuid = uuid;
		this.lastKnownName = lastKnownName;
		this.role = role;
		this.joinedAtEpochMillis = joinedAtEpochMillis;
	}

	public UUID getUuid() {
		return uuid;
	}

	public String getLastKnownName() {
		return lastKnownName;
	}

	public void setLastKnownName(String lastKnownName) {
		this.lastKnownName = lastKnownName;
	}

	public ClanRole getRole() {
		return role;
	}

	public void setRole(ClanRole role) {
		this.role = role;
	}

	public long getJoinedAtEpochMillis() {
		return joinedAtEpochMillis;
	}
}
