package com.glamardor.flashbackinspector.mixin;

import com.glamardor.flashbackinspector.record.InspectorRecorder;
import com.moulberry.flashback.record.Recorder;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * Puts the inventory into every snapshot Flashback writes.
 *
 * <p>{@code writeCustomSnapshot} is an empty method Flashback keeps for this, with a comment in the
 * source inviting mods to mixin here. Without it the inspector would only know about state it had
 * watched go past, and seeking — which is most of how a replay is used — would show an empty bag.
 */
@Mixin(value = Recorder.class, remap = false)
public class RecorderMixin {
	@Inject(method = "writeCustomSnapshot", at = @At("HEAD"))
	private void flashbackinspector$appendInventory(Consumer<Packet<? super ClientPlayPacketListener>> consumer,
			CallbackInfo ci) {
		InspectorRecorder.get().appendSnapshot(consumer);
	}
}
