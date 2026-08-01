package com.optimistopti.clanchat.clan;

/**
 * Отдельные права, которые можно включать/выключать для кастомных ролей.
 * У {@link ClanRole#LEADER} есть всегда все права, независимо от флагов.
 */
public enum ClanPermission {
	INVITE,
	KICK,
	PROMOTE_DEMOTE,
	EDIT_CLAN_INFO,
	MANAGE_HOMES,
	DISBAND,
	SEND_OFFICER_CHAT
}
