package com.alabtnt.bingo.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ElytraSyncPayload(String itemId, int count, int damage) implements CustomPacketPayload {
	public static final Type<ElytraSyncPayload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath("bingo_ourselves", "elytra_sync"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ElytraSyncPayload> CODEC =
			StreamCodec.of((buf, payload) -> {
				buf.writeUtf(payload.itemId());
				buf.writeVarInt(payload.count());
				buf.writeVarInt(payload.damage());
			}, buf -> new ElytraSyncPayload(buf.readUtf(), buf.readVarInt(), buf.readVarInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
