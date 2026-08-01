package com.optimistopti.clanchat.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Общий помощник для payload-ов, которые мы гоняем как одну JSON-строку внутри пакета.
 * <p>
 * Мы намеренно НЕ пишем по отдельному {@code StreamCodec.composite(...)} на каждое поле
 * каждого пакета: вместо этого сериализуем доменный объект в JSON через
 * {@link com.optimistopti.clanchat.clan.ClanGson} и шлём одну строку. Это немного менее
 * компактно по трафику, зато сильно снижает риск сломать сборку при любых будущих
 * изменениях состава полей — метод остаётся один и тот же для всех типов пакетов.
 */
public final class JsonPayload {

	/** {@code writeUtf}/{@code readUtf} без явного лимита — используют дефолтный максимум (32767). */
	public static final StreamCodec<RegistryFriendlyByteBuf, String> STRING_CODEC = StreamCodec.of(
			(buf, value) -> buf.writeUtf(value),
			buf -> buf.readUtf()
	);

	private JsonPayload() {
	}
}
