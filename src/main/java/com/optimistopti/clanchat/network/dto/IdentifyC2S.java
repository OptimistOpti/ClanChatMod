package com.optimistopti.clanchat.network.dto;

/**
 * {@link com.optimistopti.clanchat.network.ClanAction#IDENTIFY}
 * Первое сообщение после открытия WebSocket-соединения. Бэкенд доверяет нику как есть —
 * это осознанный компромисс для серверов без обязательной лицензии (offline-mode),
 * см. README backend/README.md.
 */
public class IdentifyC2S {
	public String name;
	/** Детерминированный offline-UUID (алгоритм как у ванильного offline-mode), для стабильного id. */
	public String uuid;
}
