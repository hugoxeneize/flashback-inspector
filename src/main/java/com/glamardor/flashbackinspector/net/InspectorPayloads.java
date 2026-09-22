package com.glamardor.flashbackinspector.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The three things this mod puts into a replay.
 *
 * <p>They ride the recording as ordinary clientbound custom payloads, which is the one kind of
 * packet Flashback copies into the file byte for byte and hands back to the viewing client
 * untouched. Two consequences worth remembering. A replay recorded with this mod opens perfectly
 * well without it: every action in the file is length prefixed on its own, so an unknown payload is
 * read as opaque bytes and thrown away without disturbing the packet after it. And nothing here may
 * ever be sent to a server — these only exist on the way into a file and on the way out of one.
 */
public final class InspectorPayloads {
	public static final String NAMESPACE = "flashbackinspector";

	private InspectorPayloads() {
	}

	private static Identifier id(String path) {
		return Identifier.of(NAMESPACE, path);
	}

	/** One slot and what was in it. */
	public record SlotChange(int slot, ItemStack stack) {
		public static final PacketCodec<RegistryByteBuf, SlotChange> CODEC = PacketCodec.tuple(
				PacketCodecs.VAR_INT, SlotChange::slot,
				ItemStack.OPTIONAL_PACKET_CODEC, SlotChange::stack,
				SlotChange::new);
	}

	/**
	 * Everything at once: who was recording, their whole inventory, and the container they had open
	 * if there was one.
	 *
	 * <p>Sent when recording starts and again into every one of Flashback's snapshots. That second
	 * one is what makes seeking work — jump anywhere in the replay and Flashback rebuilds the world
	 * from the nearest snapshot, so the inventory has to be in there too or it would arrive empty.
	 */
	public record Snapshot(UUID owner, List<ItemStack> inventory, Optional<ContainerView> container)
			implements CustomPayload {
		public static final CustomPayload.Id<Snapshot> ID = new CustomPayload.Id<>(id("snapshot"));
		public static final PacketCodec<RegistryByteBuf, Snapshot> CODEC = PacketCodec.tuple(
				Uuids.PACKET_CODEC.cast(), Snapshot::owner,
				ItemStack.OPTIONAL_PACKET_CODEC.collect(PacketCodecs.toList()), Snapshot::inventory,
				PacketCodecs.optional(ContainerView.CODEC), Snapshot::container,
				Snapshot::new);

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** An open container, described in full. */
	public record ContainerView(int containerId, Text title, List<ItemStack> contents, Optional<BlockPos> pos) {
		public static final PacketCodec<RegistryByteBuf, ContainerView> CODEC = PacketCodec.tuple(
				PacketCodecs.VAR_INT, ContainerView::containerId,
				TextCodecs.PACKET_CODEC, ContainerView::title,
				ItemStack.OPTIONAL_PACKET_CODEC.collect(PacketCodecs.toList()), ContainerView::contents,
				PacketCodecs.optional(BlockPos.PACKET_CODEC.cast()), ContainerView::pos,
				ContainerView::new);
	}

	/**
	 * Slots that changed since the previous tick.
	 *
	 * <p>{@code containerId} of zero means the player's own inventory; anything else is the sync id
	 * of the container that was open, which is what makes an item visibly travel from one grid to
	 * the other when the replay is watched with the inspector up.
	 */
	public record Slots(int containerId, List<SlotChange> changes) implements CustomPayload {
		public static final CustomPayload.Id<Slots> ID = new CustomPayload.Id<>(id("slots"));
		public static final PacketCodec<RegistryByteBuf, Slots> CODEC = PacketCodec.tuple(
				PacketCodecs.VAR_INT, Slots::containerId,
				SlotChange.CODEC.collect(PacketCodecs.toList()), Slots::changes,
				Slots::new);

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** A container was opened, with everything that was in it, or the open one was closed. */
	public record ContainerEvent(boolean opened, ContainerView view) implements CustomPayload {
		public static final CustomPayload.Id<ContainerEvent> ID = new CustomPayload.Id<>(id("container"));
		public static final PacketCodec<RegistryByteBuf, ContainerEvent> CODEC = PacketCodec.tuple(
				PacketCodecs.BOOLEAN.<RegistryByteBuf>cast(), ContainerEvent::opened,
				ContainerView.CODEC, ContainerEvent::view,
				ContainerEvent::new);

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}
