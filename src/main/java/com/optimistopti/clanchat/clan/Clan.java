package com.optimistopti.clanchat.clan;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Клан целиком: метаданные + участники + права ролей + общие точки.
 * Живёт на сервере, синхронизируется на клиент в урезанном ({@code ClanStatePayload})
 * виде через сеть.
 */
public class Clan {
	private final UUID id;
	private String name;
	/** Короткий тег клана, до 4 символов, показывается перед ником в обычном чате. */
	private String tag;
	private int color;
	private final long createdAtEpochMillis;

	private final Map<UUID, ClanMember> members = new LinkedHashMap<>();
	private final Map<ClanRole, Set<ClanPermission>> rolePermissions = new HashMap<>();
	private final Map<String, ClanHome> homes = new LinkedHashMap<>();

	public Clan(UUID id, String name, String tag, int color, UUID founder, String founderName, long createdAtEpochMillis) {
		this.id = id;
		this.name = name;
		this.tag = tag;
		this.color = color;
		this.createdAtEpochMillis = createdAtEpochMillis;

		for (ClanRole role : ClanRole.values()) {
			rolePermissions.put(role, ClanRole.defaultPermissionsFor(role));
		}

		members.put(founder, new ClanMember(founder, founderName, ClanRole.LEADER, createdAtEpochMillis));
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getTag() {
		return tag;
	}

	public void setTag(String tag) {
		this.tag = tag;
	}

	public int getColor() {
		return color;
	}

	public void setColor(int color) {
		this.color = color;
	}

	public long getCreatedAtEpochMillis() {
		return createdAtEpochMillis;
	}

	public Map<UUID, ClanMember> getMembers() {
		return members;
	}

	public Map<String, ClanHome> getHomes() {
		return homes;
	}

	public Set<ClanPermission> getRolePermissions(ClanRole role) {
		return rolePermissions.getOrDefault(role, Set.of());
	}

	public void setRolePermissions(ClanRole role, Set<ClanPermission> permissions) {
		rolePermissions.put(role, permissions);
	}

	public boolean hasPermission(UUID playerUuid, ClanPermission permission) {
		ClanMember member = members.get(playerUuid);
		if (member == null) {
			return false;
		}
		if (member.getRole() == ClanRole.LEADER) {
			return true;
		}
		return getRolePermissions(member.getRole()).contains(permission);
	}

	public boolean isMember(UUID playerUuid) {
		return members.containsKey(playerUuid);
	}

	public ClanMember getLeader() {
		return members.values().stream()
				.filter(m -> m.getRole() == ClanRole.LEADER)
				.findFirst()
				.orElse(null);
	}
}
