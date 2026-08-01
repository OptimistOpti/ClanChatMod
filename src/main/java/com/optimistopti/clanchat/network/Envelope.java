package com.optimistopti.clanchat.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.optimistopti.clanchat.clan.ClanGson;

/**
 * {@code {"action": "...", "data": {...}}} — тело обоих сетевых пейлоадов мода.
 * {@code data} хранится как сырой {@link JsonObject}, конкретная форма зависит от {@link ClanAction}
 * и десериализуется в нужный DTO вручную в обработчике действия.
 */
public class Envelope {
	public String action;
	public JsonObject data;

	public Envelope(ClanAction action, JsonObject data) {
		this.action = action.name();
		this.data = data;
	}

	/** Пустая конструктор нужен Gson для десериализации входящих пакетов. */
	public Envelope() {
	}

	public ClanAction actionEnum() {
		return ClanAction.valueOf(action);
	}

	public String toJson() {
		return ClanGson.INSTANCE.toJson(this);
	}

	public static Envelope fromJson(String json) {
		return ClanGson.INSTANCE.fromJson(json, Envelope.class);
	}

	public <T> T dataAs(Class<T> type) {
		return ClanGson.INSTANCE.fromJson(data, type);
	}

	public static JsonObject toDataObject(Object dto) {
		JsonElement element = ClanGson.INSTANCE.toJsonTree(dto);
		return element.getAsJsonObject();
	}
}
