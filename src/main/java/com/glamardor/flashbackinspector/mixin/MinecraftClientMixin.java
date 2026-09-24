package com.glamardor.flashbackinspector.mixin;

import com.glamardor.flashbackinspector.FlashbackInspector;
import com.glamardor.flashbackinspector.screen.InspectorScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands the inventory key a screen with something on it.
 *
 * <p>Catching the screen rather than the key press on purpose: in a replay the inventory key opens
 * the spectator's own inventory, which is empty by definition, and every route that opens it – the
 * key, a mod, a macro – ends at this one call.
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void flashbackinspector$openInspector(Screen screen, CallbackInfo ci) {
		if (!(screen instanceof InventoryScreen) || !FlashbackInspector.shouldReplaceInventoryKey()) {
			return;
		}
		AbstractClientPlayerEntity target = InspectorScreen.defaultTarget();
		if (target == null) {
			return;
		}
		ci.cancel();
		// Not recursion: the inspector is not an InventoryScreen, so the second call passes through.
		MinecraftClient.getInstance().setScreen(new InspectorScreen(target));
	}
}
