package com.alabtnt.bingo.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SortPayload(int containerId, boolean container, boolean player) implements CustomPacketPayload {
	public static final Type<SortPayload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath("bingo_ourselves", "sort"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SortPayload> CODEC =
			StreamCodec.of((buf, payload) -> {
				buf.writeVarInt(payload.containerId());
				buf.writeBoolean(payload.container());
				buf.writeBoolean(payload.player());
			}, buf -> new SortPayload(buf.readVarInt(), buf.readBoolean(), buf.readBoolean()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
