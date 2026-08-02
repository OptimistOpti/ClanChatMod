package com.optimistopti.clanchat.clan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.UUID;

/**
 * Общий Gson для сериализации кланов на диск и данных вложений чата в JSON-строку,
 * которая гоняется по сети внутри {@code StreamCodec} пейлоадов.
 */
public final class ClanGson {

	public static final Gson INSTANCE = new GsonBuilder()
			.setPrettyPrinting()
			.registerTypeAdapter(UUID.class, new TypeAdapter<UUID>() {
				@Override
				public void write(JsonWriter out, UUID value) throws IOException {
					if (value == null) {
						out.nullValue();
					} else {
						out.value(value.toString());
					}
				}

				@Override
				public UUID read(JsonReader in) throws IOException {
					if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
						in.nextNull();
						return null;
					}
					return UUID.fromString(in.nextString());
				}
			})
			.create();

	private ClanGson() {
	}
}
