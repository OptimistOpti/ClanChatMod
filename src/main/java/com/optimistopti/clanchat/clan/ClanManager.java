package com.optimistopti.clanchat.clan;

import com.optimistopti.clanchat.ClanChatMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Единая точка входа для всей серверной логики кланов. Один экземпляр на запущенный сервер,
 * см. {@link ClanChatMod} (создаётся в {@code ServerLifecycleEvents.SERVER_STARTED},
 * освобождается в {@code SERVER_STOPPING}).
 */
public class ClanManager {

	private static final Pattern NAME_PATTERN = Pattern.compile("^[\\p{L}0-9 _-]{3,24}$");
	private static final Pattern TAG_PATTERN = Pattern.compile("^[\\p{L}0-9]{2,4}$");

	private final MinecraftServer server;
	private final ClanStorage storage;

	private final Map<UUID, Clan> clansById = new HashMap<>();
	/** playerUuid -> clanId, для быстрого поиска клана игрока. */
	private final Map<UUID, UUID> membership = new HashMap<>();
	/** targetPlayerUuid -> активное приглашение (одно за раз, простоты ради). */
	private final Map<UUID, ClanInvite> pendingInvites = new HashMap<>();

	public ClanManager(MinecraftServer server) {
		this.server = server;
		this.storage = new ClanStorage(server.getWorldPath(LevelResource.ROOT));
		for (Clan clan : storage.loadAll()) {
			clansById.put(clan.getId(), clan);
			for (UUID member : clan.getMembers().keySet()) {
				membership.put(member, clan.getId());
			}
		}
		ClanChatMod.LOGGER.info("Загружено кланов: {}", clansById.size());
	}

	// ---------------------------------------------------------------- lookups

	public Clan getClan(UUID clanId) {
		return clansById.get(clanId);
	}

	public Clan getClanOf(UUID playerUuid) {
		UUID clanId = membership.get(playerUuid);
		return clanId == null ? null : clansById.get(clanId);
	}

	public ClanInvite getPendingInvite(UUID playerUuid) {
		ClanInvite invite = pendingInvites.get(playerUuid);
		if (invite != null && invite.isExpired(System.currentTimeMillis())) {
			pendingInvites.remove(playerUuid);
			return null;
		}
		return invite;
	}

	// ---------------------------------------------------------------- create / disband

	public Clan createClan(ServerPlayer founder, String name, String tag, int color) throws ClanActionException {
		if (getClanOf(founder.getUUID()) != null) {
			throw new ClanActionException("Ты уже состоишь в клане. Сначала покинь текущий клан.");
		}
		if (!NAME_PATTERN.matcher(name).matches()) {
			throw new ClanActionException("Название клана должно быть 3-24 символа (буквы/цифры/пробел/-/_).");
		}
		if (!TAG_PATTERN.matcher(tag).matches()) {
			throw new ClanActionException("Тег клана должен быть 2-4 символа (буквы/цифры).");
		}
		for (Clan existing : clansById.values()) {
			if (existing.getName().equalsIgnoreCase(name)) {
				throw new ClanActionException("Клан с таким названием уже существует.");
			}
			if (existing.getTag().equalsIgnoreCase(tag)) {
				throw new ClanActionException("Клан с таким тегом уже существует.");
			}
		}

		Clan clan = new Clan(UUID.randomUUID(), name, tag, color, founder.getUUID(), founder.getName().getString(), System.currentTimeMillis());
		clansById.put(clan.getId(), clan);
		membership.put(founder.getUUID(), clan.getId());
		storage.save(clan);
		ClanChatMod.LOGGER.info("Игрок {} создал клан '{}' [{}]", founder.getName().getString(), name, tag);
		return clan;
	}

	public void disbandClan(ServerPlayer actor) throws ClanActionException {
		Clan clan = requireClan(actor);
		if (!clan.hasPermission(actor.getUUID(), ClanPermission.DISBAND)) {
			throw new ClanActionException("У тебя нет прав распустить клан.");
		}
		for (UUID member : clan.getMembers().keySet()) {
			membership.remove(member);
		}
		clansById.remove(clan.getId());
		storage.delete(clan.getId());
		ClanChatMod.LOGGER.info("Клан '{}' распущен игроком {}", clan.getName(), actor.getName().getString());
	}

	// ---------------------------------------------------------------- invites

	public void invite(ServerPlayer actor, ServerPlayer target) throws ClanActionException {
		Clan clan = requireClan(actor);
		if (!clan.hasPermission(actor.getUUID(), ClanPermission.INVITE)) {
			throw new ClanActionException("У тебя нет прав приглашать в клан.");
		}
		if (getClanOf(target.getUUID()) != null) {
			throw new ClanActionException("Этот игрок уже состоит в клане.");
		}
		if (target.getUUID().equals(actor.getUUID())) {
			throw new ClanActionException("Нельзя пригласить самого себя.");
		}
		ClanInvite invite = new ClanInvite(clan.getId(), clan.getName(), actor.getUUID(),
				actor.getName().getString(), target.getUUID(), System.currentTimeMillis());
		pendingInvites.put(target.getUUID(), invite);
	}

	public Clan acceptInvite(ServerPlayer player) throws ClanActionException {
		ClanInvite invite = getPendingInvite(player.getUUID());
		if (invite == null) {
			throw new ClanActionException("У тебя нет активных приглашений.");
		}
		if (getClanOf(player.getUUID()) != null) {
			throw new ClanActionException("Ты уже состоишь в клане.");
		}
		Clan clan = clansById.get(invite.clanId());
		if (clan == null) {
			pendingInvites.remove(player.getUUID());
			throw new ClanActionException("Этот клан больше не существует.");
		}
		clan.getMembers().put(player.getUUID(),
				new ClanMember(player.getUUID(), player.getName().getString(), ClanRole.MEMBER, System.currentTimeMillis()));
		membership.put(player.getUUID(), clan.getId());
		pendingInvites.remove(player.getUUID());
		storage.save(clan);
		return clan;
	}

	public void declineInvite(ServerPlayer player) {
		pendingInvites.remove(player.getUUID());
	}

	// ---------------------------------------------------------------- membership management

	public void kick(ServerPlayer actor, UUID targetUuid) throws ClanActionException {
		Clan clan = requireClan(actor);
		if (!clan.hasPermission(actor.getUUID(), ClanPermission.KICK)) {
			throw new ClanActionException("У тебя нет прав кикать из клана.");
		}
		ClanMember target = clan.getMembers().get(targetUuid);
		if (target == null) {
			throw new ClanActionException("Этот игрок не в клане.");
		}
		if (target.getRole() == ClanRole.LEADER) {
			throw new ClanActionException("Нельзя кикнуть лидера клана.");
		}
		clan.getMembers().remove(targetUuid);
		membership.remove(targetUuid);
		storage.save(clan);
	}

	public void leave(ServerPlayer player) throws ClanActionException {
		Clan clan = requireClan(player);
		ClanMember member = clan.getMembers().get(player.getUUID());
		if (member.getRole() == ClanRole.LEADER && clan.getMembers().size() > 1) {
			throw new ClanActionException("Сначала передай лидерство другому участнику или кикни всех.");
		}
		clan.getMembers().remove(player.getUUID());
		membership.remove(player.getUUID());
		if (clan.getMembers().isEmpty()) {
			clansById.remove(clan.getId());
			storage.delete(clan.getId());
		} else {
			storage.save(clan);
		}
	}

	public void setRole(ServerPlayer actor, UUID targetUuid, ClanRole newRole) throws ClanActionException {
		Clan clan = requireClan(actor);
		if (!clan.hasPermission(actor.getUUID(), ClanPermission.PROMOTE_DEMOTE)) {
			throw new ClanActionException("У тебя нет прав менять роли.");
		}
		ClanMember target = clan.getMembers().get(targetUuid);
		if (target == null) {
			throw new ClanActionException("Этот игрок не в клане.");
		}
		if (newRole == ClanRole.LEADER) {
			// Передача лидерства: только текущий лидер может это сделать.
			ClanMember actorMember = clan.getMembers().get(actor.getUUID());
			if (actorMember.getRole() != ClanRole.LEADER) {
				throw new ClanActionException("Только лидер может передать лидерство.");
			}
			actorMember.setRole(ClanRole.OFFICER);
		}
		target.setRole(newRole);
		storage.save(clan);
	}

	// ---------------------------------------------------------------- misc

	public void updateLastKnownName(ServerPlayer player) {
		Clan clan = getClanOf(player.getUUID());
		if (clan != null) {
			ClanMember member = clan.getMembers().get(player.getUUID());
			if (member != null) {
				member.setLastKnownName(player.getName().getString());
			}
		}
	}

	public void saveAll() {
		for (Clan clan : clansById.values()) {
			storage.save(clan);
		}
	}

	private Clan requireClan(ServerPlayer player) throws ClanActionException {
		Clan clan = getClanOf(player.getUUID());
		if (clan == null) {
			throw new ClanActionException("Ты не состоишь в клане.");
		}
		return clan;
	}

	public List<Clan> getAllClans() {
		return List.copyOf(clansById.values());
	}
}
