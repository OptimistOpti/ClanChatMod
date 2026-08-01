package com.optimistopti.clanchat.network.payload;

import com.optimistopti.clanchat.ClanChatMod;
import com.optimistopti.clanchat.network.JsonPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Единый пакет сервер -> клиент. См. {@link ClanChatC2SPayload} — тот же подход
 * с JSON-конвертом вместо отдельного payload-класса на каждое действие.
 */
public record ClanChatS2CPayload(String json) implements CustomPacketPayload {

	public static final Identifier ID = Identifier.fromNamespaceAndPath(ClanChatMod.MOD_ID, "s2c");
	public static final CustomPacketPayload.Type<ClanChatS2CPayload> TYPE = new CustomPacketPayload.Type<>(ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, ClanChatS2CPayload> CODEC = StreamCodec.composite(
			JsonPayload.STRING_CODEC, ClanChatS2CPayload::json,
			ClanChatS2CPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
