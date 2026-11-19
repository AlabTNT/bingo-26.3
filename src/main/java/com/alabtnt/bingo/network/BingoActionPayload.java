package com.alabtnt.bingo.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record BingoActionPayload(String action, String arg) implements CustomPacketPayload {
	public static final Type<BingoActionPayload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath("bingo_ourselves", "action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, BingoActionPayload> CODEC =
			StreamCodec.of((buf, payload) -> {
				buf.writeUtf(payload.action());
				buf.writeUtf(payload.arg() == null ? "" : payload.arg());
			}, buf -> new BingoActionPayload(buf.readUtf(), buf.readUtf()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
