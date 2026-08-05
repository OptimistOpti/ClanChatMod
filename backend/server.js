'use strict';

const { WebSocketServer } = require('ws');
const crypto = require('crypto');
const { ClanStore, ClanActionError } = require('./lib/clanStore');
const { ROLE_COLOR } = require('./lib/permissions');

const PORT = process.env.PORT || process.env.CLANCHAT_PORT || 8080;
const MAX_MESSAGE_LENGTH = 512;
// Скриншоты (base64 PNG) кладём тоже как вложение, отсюда такой большой лимит —
// см. ScreenshotCapture.java на стороне мода (уменьшает и сжимает картинку перед отправкой).
const MAX_ATTACHMENT_JSON_LENGTH = 400000;

const store = new ClanStore();

// uuid -> ws (только для игроков, у кого сейчас открыт клиент с этим модом)
const connectedByUuid = new Map();
// ws -> { name, uuid } — заполняется после IDENTIFY
const identityByWs = new Map();

const wss = new WebSocketServer({ port: PORT });
console.log(`[ClanChat backend] слушаю ws://0.0.0.0:${PORT}`);

wss.on('connection', (ws) => {
	ws.isAlive = true;
	ws.on('pong', () => { ws.isAlive = true; });

	ws.on('message', (raw) => {
		let envelope;
		try {
			envelope = JSON.parse(raw.toString());
		} catch (e) {
			console.warn('[ClanChat backend] некорректный JSON от клиента:', e.message);
			return;
		}
		try {
			handleEnvelope(ws, envelope);
		} catch (e) {
			if (e instanceof ClanActionError) {
				sendTo(ws, 'SYSTEM_NOTICE', { text: e.message, level: 'error' });
			} else {
				console.error('[ClanChat backend] ошибка обработки', envelope && envelope.action, e);
				sendTo(ws, 'SYSTEM_NOTICE', { text: 'Внутренняя ошибка бэкенда, попробуй ещё раз.', level: 'error' });
			}
		}
	});

	ws.on('close', () => {
		const identity = identityByWs.get(ws);
		if (identity && connectedByUuid.get(identity.uuid) === ws) {
			connectedByUuid.delete(identity.uuid);
		}
		identityByWs.delete(ws);
	});
});

// Heartbeat: убиваем зависшие соединения (частая причина — таймаут прокси/хостинга).
const heartbeat = setInterval(() => {
	for (const ws of wss.clients) {
		if (ws.isAlive === false) {
			ws.terminate();
			continue;
		}
		ws.isAlive = false;
		ws.ping();
	}
}, 30000);
wss.on('close', () => clearInterval(heartbeat));

function sendTo(ws, action, data) {
	if (ws.readyState !== ws.OPEN) return;
	ws.send(JSON.stringify({ action, data }));
}

function sendToUuid(uuid, action, data) {
	const ws = connectedByUuid.get(uuid);
	if (ws) sendTo(ws, action, data);
}

function sendFullState(uuid) {
	const clan = store.getClanOf(uuid);
	if (!clan) {
		sendToUuid(uuid, 'NO_CLAN', {});
		return;
	}
	sendToUuid(uuid, 'CLAN_STATE', clan);
	sendToUuid(uuid, 'CHAT_HISTORY', { messages: store.getRecentMessages(clan.id) });
}

function broadcastStateToClanMembers(clan) {
	for (const uuid of Object.keys(clan.members)) {
		sendFullState(uuid);
	}
}

function requireIdentity(ws) {
	const identity = identityByWs.get(ws);
	if (!identity) {
		throw new ClanActionError('Сначала нужно представиться (IDENTIFY) — переподключись.');
	}
	return identity;
}

function handleEnvelope(ws, envelope) {
	const { action, data } = envelope;

	if (action === 'IDENTIFY') {
		const name = String(data.name || '').slice(0, 32) || 'Player';
		const uuid = String(data.uuid || '');
		if (!uuid) {
			sendTo(ws, 'SYSTEM_NOTICE', { text: 'Некорректная идентификация.', level: 'error' });
			return;
		}
		identityByWs.set(ws, { name, uuid });
		connectedByUuid.set(uuid, ws);
		store.updateLastKnownName({ name, uuid });
		console.log(`[ClanChat backend] ${name} (${uuid}) подключился`);
		return;
	}

	const identity = requireIdentity(ws);
	console.log(`[ClanChat backend] <- ${identity.name}: ${action}`, action === 'SEND_MESSAGE' ? data.content : '');

	switch (action) {
		case 'SEND_MESSAGE':
			return handleSendMessage(identity, data);
		case 'CREATE_CLAN': {
			store.createClan(identity, data.name, data.tag, data.color);
			return sendFullState(identity.uuid);
		}
		case 'INVITE':
			return handleInvite(identity, data);
		case 'ACCEPT_INVITE': {
			const clan = store.acceptInvite(identity);
			return broadcastStateToClanMembers(clan);
		}
		case 'DECLINE_INVITE':
			return store.declineInvite(identity.uuid);
		case 'KICK': {
			const clan = store.kick(identity.uuid, data.targetUuid);
			broadcastStateToClanMembers(clan);
			return sendFullState(data.targetUuid);
		}
		case 'SET_ROLE': {
			const clan = store.setRole(identity.uuid, data.targetUuid, data.role);
			return broadcastStateToClanMembers(clan);
		}
		case 'LEAVE': {
			store.leave(identity.uuid);
			return sendFullState(identity.uuid);
		}
		case 'DISBAND': {
			const clan = store.getClanOf(identity.uuid);
			const clanCopy = clan ? { ...clan, members: { ...clan.members } } : null;
			store.disbandClan(identity.uuid);
			if (clanCopy) {
				for (const uuid of Object.keys(clanCopy.members)) {
					sendToUuid(uuid, 'NO_CLAN', {});
				}
			}
			return;
		}
		case 'REQUEST_STATE':
			return sendFullState(identity.uuid);
		default:
			console.warn('[ClanChat backend] неизвестное действие:', action);
	}
}

function handleInvite(identity, data) {
	const targetName = String(data.targetName || '');
	let targetUuid = null;
	for (const [uuid, ws] of connectedByUuid.entries()) {
		const other = identityByWs.get(ws);
		if (other && other.name.toLowerCase() === targetName.toLowerCase()) {
			targetUuid = uuid;
			break;
		}
	}
	if (!targetUuid) {
		throw new ClanActionError(`Игрок с ником '${targetName}' сейчас не подключён к ClanChat.`);
	}
	const invite = store.invite(identity.uuid, identity.name, { uuid: targetUuid });
	sendToUuid(targetUuid, 'INVITE_RECEIVED', { clanName: invite.clanName, inviterName: invite.inviterName });
	sendToUuid(identity.uuid, 'SYSTEM_NOTICE', { text: `Приглашение отправлено игроку ${targetName}.`, level: 'info' });
}

function handleSendMessage(identity, data) {
	const clan = store.getClanOf(identity.uuid);
	if (!clan) {
		throw new ClanActionError('Ты не состоишь в клане.');
	}
	const channel = data.channel;
	const senderMember = clan.members[identity.uuid];

	if (channel === 'OFFICERS' && senderMember.role !== 'LEADER' && !store.hasPermission(clan, identity.uuid, 'SEND_OFFICER_CHAT')) {
		throw new ClanActionError('У тебя нет доступа к чату заместителей.');
	}
	if (channel === 'SYSTEM') {
		throw new ClanActionError('Нельзя отправлять сообщения в системный канал.');
	}

	let content = (data.content || '').trim();
	if (!content && !data.attachmentType) {
		return;
	}
	if (content.length > MAX_MESSAGE_LENGTH) {
		content = content.slice(0, MAX_MESSAGE_LENGTH);
	}

	let attachment = null;
	if (data.attachmentType) {
		if (!data.attachmentDataJson) {
			throw new ClanActionError('Вложение пустое — не удалось прикрепить.');
		}
		if (data.attachmentDataJson.length > MAX_ATTACHMENT_JSON_LENGTH) {
			throw new ClanActionError(
				`Вложение слишком большое (${data.attachmentDataJson.length} симв., лимит ${MAX_ATTACHMENT_JSON_LENGTH}) — не отправлено. `
				+ 'Если это скриншот — возможно, на бэкенде ещё старый лимит, обнови server.js.');
		}
		attachment = { type: data.attachmentType, dataJson: data.attachmentDataJson };
	}

	let whisperTargetUuid = null;
	let whisperTargetName = null;
	if (channel === 'WHISPER' && data.whisperTargetUuid) {
		whisperTargetUuid = data.whisperTargetUuid;
		const targetMember = clan.members[whisperTargetUuid];
		if (!targetMember) {
			throw new ClanActionError('Этот игрок не в твоём клане.');
		}
		whisperTargetName = targetMember.lastKnownName;
	}

	const message = {
		id: crypto.randomUUID(),
		channel,
		clanId: clan.id,
		senderUuid: identity.uuid,
		senderName: identity.name,
		senderColor: ROLE_COLOR[senderMember.role] || ROLE_COLOR.MEMBER,
		whisperTargetUuid,
		whisperTargetName,
		content,
		timestampEpochMillis: Date.now(),
		attachment,
	};

	store.addMessage(clan.id, message);

	let delivered = 0;
	for (const uuid of Object.keys(clan.members)) {
		if (channel === 'WHISPER' && uuid !== identity.uuid && uuid !== whisperTargetUuid) {
			continue;
		}
		if (channel === 'OFFICERS' && clan.members[uuid].role !== 'LEADER'
				&& !store.hasPermission(clan, uuid, 'SEND_OFFICER_CHAT')) {
			continue;
		}
		if (connectedByUuid.has(uuid)) delivered++;
		sendToUuid(uuid, 'CHAT_MESSAGE', message);
	}
	console.log(`[ClanChat backend] сообщение в клане ${clan.name} доставлено ${delivered} из ${Object.keys(clan.members).length} онлайн-участников`);
}
