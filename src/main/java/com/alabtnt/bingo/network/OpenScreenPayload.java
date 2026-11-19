package com.alabtnt.bingo.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenScreenPayload(String screen) implements CustomPacketPayload {
	public static final Type<OpenScreenPayload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath("bingo_ourselves", "open_screen"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OpenScreenPayload> CODEC =
			StreamCodec.of((buf, payload) -> buf.writeUtf(payload.screen()),
					buf -> new OpenScreenPayload(buf.readUtf()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
