'use strict';

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { DEFAULT_PERMISSIONS } = require('./permissions');

const DATA_DIR = process.env.CLANCHAT_DATA_DIR || path.join(__dirname, '..', 'data');
const CLANS_FILE = path.join(DATA_DIR, 'clans.json');
const INVITE_EXPIRY_MS = 5 * 60 * 1000;
const MAX_MESSAGES_PER_CLAN = 200;

const NAME_PATTERN = /^[\p{L}0-9 _-]{3,24}$/u;
const TAG_PATTERN = /^[\p{L}0-9]{2,4}$/u;

class ClanActionError extends Error {
}

/**
 * Держит все кланы в памяти + пишет на диск при каждой мутации (простой JSON-файл,
 * этого более чем достаточно для масштаба "друзья играют вместе").
 * Зеркалит семантику Java-версии (ClanManager/ClanStorage) один в один, чтобы протокол
 * не разъезжался с тем, что ожидает клиентский мод.
 */
class ClanStore {
	constructor() {
		this.clans = new Map(); // id -> clan
		this.membership = new Map(); // uuid -> clanId
		this.pendingInvites = new Map(); // targetUuid -> invite
		this.history = new Map(); // clanId -> ChatMessage[]
		this._load();
	}

	_load() {
		fs.mkdirSync(DATA_DIR, { recursive: true });
		if (!fs.existsSync(CLANS_FILE)) {
			return;
		}
		try {
			const raw = JSON.parse(fs.readFileSync(CLANS_FILE, 'utf8'));
			for (const clan of raw) {
				this.clans.set(clan.id, clan);
				for (const uuid of Object.keys(clan.members)) {
					this.membership.set(uuid, clan.id);
				}
			}
			console.log(`[ClanStore] загружено кланов: ${this.clans.size}`);
		} catch (e) {
			console.error('[ClanStore] не удалось прочитать clans.json:', e);
		}
	}

	saveAll() {
		const tmp = CLANS_FILE + '.tmp';
		fs.writeFileSync(tmp, JSON.stringify([...this.clans.values()], null, 2), 'utf8');
		fs.renameSync(tmp, CLANS_FILE);
	}

	getClan(id) {
		return this.clans.get(id) || null;
	}

	getClanOf(uuid) {
		const clanId = this.membership.get(uuid);
		return clanId ? this.clans.get(clanId) : null;
	}

	hasPermission(clan, uuid, permission) {
		const member = clan.members[uuid];
		if (!member) return false;
		if (member.role === 'LEADER') return true;
		const perms = clan.rolePermissions[member.role] || [];
		return perms.includes(permission);
	}

	getPendingInvite(uuid) {
		const invite = this.pendingInvites.get(uuid);
		if (invite && Date.now() - invite.createdAt > INVITE_EXPIRY_MS) {
			this.pendingInvites.delete(uuid);
			return null;
		}
		return invite || null;
	}

	// ---------------------------------------------------------------- create / disband

	createClan(identity, name, tag, color) {
		if (this.getClanOf(identity.uuid)) {
			throw new ClanActionError('Ты уже состоишь в клане. Сначала покинь текущий клан.');
		}
		if (!NAME_PATTERN.test(name)) {
			throw new ClanActionError('Название клана должно быть 3-24 символа (буквы/цифры/пробел/-/_).');
		}
		if (!TAG_PATTERN.test(tag)) {
			throw new ClanActionError('Тег клана должен быть 2-4 символа (буквы/цифры).');
		}
		for (const c of this.clans.values()) {
			if (c.name.toLowerCase() === name.toLowerCase()) {
				throw new ClanActionError('Клан с таким названием уже существует.');
			}
			if (c.tag.toLowerCase() === tag.toLowerCase()) {
				throw new ClanActionError('Клан с таким тегом уже существует.');
			}
		}

		const id = crypto.randomUUID();
		const now = Date.now();
		const clan = {
			id,
			name,
			tag,
			color,
			createdAtEpochMillis: now,
			members: {
				[identity.uuid]: { uuid: identity.uuid, lastKnownName: identity.name, role: 'LEADER', joinedAtEpochMillis: now },
			},
			rolePermissions: JSON.parse(JSON.stringify(DEFAULT_PERMISSIONS)),
			homes: {},
		};
		this.clans.set(id, clan);
		this.membership.set(identity.uuid, id);
		this.saveAll();
		console.log(`[ClanStore] ${identity.name} создал клан '${name}' [${tag}]`);
		return clan;
	}

	disbandClan(actorUuid) {
		const clan = this._requireClan(actorUuid);
		if (!this.hasPermission(clan, actorUuid, 'DISBAND')) {
			throw new ClanActionError('У тебя нет прав распустить клан.');
		}
		for (const uuid of Object.keys(clan.members)) {
			this.membership.delete(uuid);
		}
		this.clans.delete(clan.id);
		this.history.delete(clan.id);
		this.saveAll();
		return clan;
	}

	// ---------------------------------------------------------------- invites

	invite(actorUuid, actorName, target) {
		const clan = this._requireClan(actorUuid);
		if (!this.hasPermission(clan, actorUuid, 'INVITE')) {
			throw new ClanActionError('У тебя нет прав приглашать в клан.');
		}
		if (this.getClanOf(target.uuid)) {
			throw new ClanActionError('Этот игрок уже состоит в клане.');
		}
		if (target.uuid === actorUuid) {
			throw new ClanActionError('Нельзя пригласить самого себя.');
		}
		const inv = {
			clanId: clan.id,
			clanName: clan.name,
			inviterUuid: actorUuid,
			inviterName: actorName,
			targetUuid: target.uuid,
			createdAtEpochMillis: Date.now(),
		};
		this.pendingInvites.set(target.uuid, inv);
		return inv;
	}

	acceptInvite(identity) {
		const invite = this.getPendingInvite(identity.uuid);
		if (!invite) {
			throw new ClanActionError('У тебя нет активных приглашений.');
		}
		if (this.getClanOf(identity.uuid)) {
			throw new ClanActionError('Ты уже состоишь в клане.');
		}
		const clan = this.clans.get(invite.clanId);
		if (!clan) {
			this.pendingInvites.delete(identity.uuid);
			throw new ClanActionError('Этот клан больше не существует.');
		}
		clan.members[identity.uuid] = {
			uuid: identity.uuid, lastKnownName: identity.name, role: 'MEMBER', joinedAtEpochMillis: Date.now(),
		};
		this.membership.set(identity.uuid, clan.id);
		this.pendingInvites.delete(identity.uuid);
		this.saveAll();
		return clan;
	}

	declineInvite(uuid) {
		this.pendingInvites.delete(uuid);
	}

	// ---------------------------------------------------------------- membership management

	kick(actorUuid, targetUuid) {
		const clan = this._requireClan(actorUuid);
		if (!this.hasPermission(clan, actorUuid, 'KICK')) {
			throw new ClanActionError('У тебя нет прав кикать из клана.');
		}
		const target = clan.members[targetUuid];
		if (!target) {
			throw new ClanActionError('Этот игрок не в клане.');
		}
		if (target.role === 'LEADER') {
			throw new ClanActionError('Нельзя кикнуть лидера клана.');
		}
		delete clan.members[targetUuid];
		this.membership.delete(targetUuid);
		this.saveAll();
		return clan;
	}

	leave(uuid) {
		const clan = this._requireClan(uuid);
		const member = clan.members[uuid];
		const memberCount = Object.keys(clan.members).length;
		if (member.role === 'LEADER' && memberCount > 1) {
			throw new ClanActionError('Сначала передай лидерство другому участнику или кикни всех.');
		}
		delete clan.members[uuid];
		this.membership.delete(uuid);
		if (Object.keys(clan.members).length === 0) {
			this.clans.delete(clan.id);
			this.history.delete(clan.id);
		}
		this.saveAll();
		return clan;
	}

	setRole(actorUuid, targetUuid, newRole) {
		const clan = this._requireClan(actorUuid);
		if (!this.hasPermission(clan, actorUuid, 'PROMOTE_DEMOTE')) {
			throw new ClanActionError('У тебя нет прав менять роли.');
		}
		const target = clan.members[targetUuid];
		if (!target) {
			throw new ClanActionError('Этот игрок не в клане.');
		}
		if (newRole === 'LEADER') {
			const actorMember = clan.members[actorUuid];
			if (actorMember.role !== 'LEADER') {
				throw new ClanActionError('Только лидер может передать лидерство.');
			}
			actorMember.role = 'OFFICER';
		}
		target.role = newRole;
		this.saveAll();
		return clan;
	}

	updateLastKnownName(identity) {
		const clan = this.getClanOf(identity.uuid);
		if (clan && clan.members[identity.uuid]) {
			clan.members[identity.uuid].lastKnownName = identity.name;
		}
	}

	// ---------------------------------------------------------------- chat history

	addMessage(clanId, message) {
		if (!clanId) return;
		let list = this.history.get(clanId);
		if (!list) {
			list = [];
			this.history.set(clanId, list);
		}
		list.push(message);
		while (list.length > MAX_MESSAGES_PER_CLAN) {
			list.shift();
		}
	}

	getRecentMessages(clanId) {
		return this.history.get(clanId) || [];
	}

	_requireClan(uuid) {
		const clan = this.getClanOf(uuid);
		if (!clan) {
			throw new ClanActionError('Ты не состоишь в клане.');
		}
		return clan;
	}
}

module.exports = { ClanStore, ClanActionError };
