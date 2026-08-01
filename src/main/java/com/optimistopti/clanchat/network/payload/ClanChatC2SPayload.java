package com.optimistopti.clanchat.network.payload;

import com.optimistopti.clanchat.ClanChatMod;
import com.optimistopti.clanchat.network.JsonPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Единый пакет клиент -> сервер. Конкретное действие закодировано строкой в JSON-теле
 * (см. {@link com.optimistopti.clanchat.network.ClanAction} и {@code C2SEnvelope}).
 * <p>
 * Один payload-тип на все действия сознательно: это резко уменьшает количество мест,
 * которые можно сломать при доработке протокола, ценой чуть менее строгой типизации на
 * сетевом уровне (типизация есть на уровне {@code C2SEnvelope}/DTO в Java-коде).
 */
public record ClanChatC2SPayload(String json) implements CustomPacketPayload {

	public static final Identifier ID = Identifier.fromNamespaceAndPath(ClanChatMod.MOD_ID, "c2s");
	public static final CustomPacketPayload.Type<ClanChatC2SPayload> TYPE = new CustomPacketPayload.Type<>(ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, ClanChatC2SPayload> CODEC = StreamCodec.composite(
			JsonPayload.STRING_CODEC, ClanChatC2SPayload::json,
			ClanChatC2SPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
