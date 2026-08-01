package com.optimistopti.clanchat.clan;

import java.util.EnumSet;
import java.util.Set;

/**
 * Базовые роли внутри клана. У каждого участника ровно одна базовая роль.
 * Права роли можно тонко настраивать через {@link Clan#getRolePermissions(ClanRole)},
 * но LEADER всегда имеет полный доступ вне зависимости от настроек.
 */
public enum ClanRole {
	LEADER(4, "clanchat.role.leader", 0xFFD700),
	OFFICER(3, "clanchat.role.officer", 0x55CCFF),
	MEMBER(1, "clanchat.role.member", 0xAAAAAA);

	private final int rank;
	private final String translationKey;
	private final int defaultColor;

	ClanRole(int rank, String translationKey, int defaultColor) {
		this.rank = rank;
		this.translationKey = translationKey;
		this.defaultColor = defaultColor;
	}

	public int getRank() {
		return rank;
	}

	public String getTranslationKey() {
		return translationKey;
	}

	public int getDefaultColor() {
		return defaultColor;
	}

	/** Права по умолчанию для свежесозданного клана (до кастомизации лидером). */
	public static Set<ClanPermission> defaultPermissionsFor(ClanRole role) {
		return switch (role) {
			case LEADER -> EnumSet.allOf(ClanPermission.class);
			case OFFICER -> EnumSet.of(
					ClanPermission.INVITE,
					ClanPermission.KICK,
					ClanPermission.SEND_OFFICER_CHAT,
					ClanPermission.MANAGE_HOMES
			);
			case MEMBER -> EnumSet.noneOf(ClanPermission.class);
		};
	}
}
