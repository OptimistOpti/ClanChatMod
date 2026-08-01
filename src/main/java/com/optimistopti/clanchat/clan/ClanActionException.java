package com.optimistopti.clanchat.clan;

/**
 * Ожидаемый "бизнесовый" отказ (не хватает прав, имя занято, игрок уже в клане и т.д.).
 * Сообщение уходит игроку как есть в системный канал чата, поэтому уже человекочитаемое (RU).
 */
public class ClanActionException extends Exception {
	public ClanActionException(String message) {
		super(message);
	}
}
