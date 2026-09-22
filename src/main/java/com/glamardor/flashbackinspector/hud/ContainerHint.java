package com.glamardor.flashbackinspector.hud;

import com.glamardor.flashbackinspector.config.ContainerMode;
import com.glamardor.flashbackinspector.config.InspectorConfig;
import com.glamardor.flashbackinspector.playback.InspectorState;
import com.moulberry.flashback.Flashback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/**
 * A line saying a container was open on this tick, so the manual mode is discoverable without
 * having to guess when to press the key.
 */
public final class ContainerHint {
	private ContainerHint() {
	}

	public static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		InspectorConfig config = InspectorConfig.get();
		if (!config.showContainerHint || config.containerMode == ContainerMode.FOLLOW) {
			return;
		}
		if (client.currentScreen != null || client.options.hudHidden || !Flashback.isInReplay()) {
			return;
		}
		InspectorState state = InspectorState.get();
		if (!state.hasContainer()) {
			return;
		}

		Text line = Text.translatable("flashbackinspector.hint.container",
				state.containerTitle(), client.options.inventoryKey.getBoundKeyLocalizedText());
		int width = client.textRenderer.getWidth(line);
		int x = (context.getScaledWindowWidth() - width) / 2;
		int y = context.getScaledWindowHeight() - 59;
		context.fill(x - 4, y - 3, x + width + 4, y + 11, 0x90000000);
		context.drawTextWithShadow(client.textRenderer, line, x, y, 0xFFE0C070);
	}
}
