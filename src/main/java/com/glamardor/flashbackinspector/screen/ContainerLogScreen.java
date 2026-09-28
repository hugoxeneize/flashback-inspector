package com.glamardor.flashbackinspector.screen;

import com.glamardor.flashbackinspector.playback.InspectorState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Every container the recording player opened, with the tick it happened on. Clicking one takes the
 * replay there and stops it, which is the short way from "something was taken" to the frame it was
 * taken on.
 *
 * <p>The list fills in as the replay is watched, because that is when the packets go past. Seeking
 * over a stretch fills it in for that stretch too, so scrubbing once through a recording is enough
 * to have the whole thing.
 */
public class ContainerLogScreen extends Screen {
	private static final int ROW_WIDTH = 320;

	@Nullable
	private final Screen parent;

	public ContainerLogScreen(@Nullable Screen parent) {
		super(Text.translatable("flashbackinspector.log.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		List<InspectorState.ContainerOpening> openings = InspectorState.get().openings();
		OpeningList list = new OpeningList(this.client, this.width, this.height - 96, 40, 22);
		for (InspectorState.ContainerOpening opening : openings) {
			list.addWidget(ButtonWidget.builder(describe(opening),
							button -> InspectorState.seek(opening.tick()))
					.dimensions(0, 0, ROW_WIDTH, 20)
					.build());
		}
		addDrawableChild(list);

		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
				.dimensions(this.width / 2 - 75, this.height - 30, 150, 20)
				.build());
	}

	private static Text describe(InspectorState.ContainerOpening opening) {
		Text line = Text.literal(InspectorState.timecode(opening.tick()))
				.formatted(Formatting.GOLD)
				.append(Text.literal("  "))
				.append(opening.title().copy().formatted(Formatting.WHITE));
		BlockPos pos = opening.pos();
		if (pos != null) {
			line = line.copy().append(Text.literal("  " + pos.getX() + " " + pos.getY() + " " + pos.getZ())
					.formatted(Formatting.DARK_GRAY));
		}
		return line;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 18, 0xFFFFFFFF);
		if (!InspectorState.get().hasOpenings()) {
			context.drawCenteredTextWithShadow(this.textRenderer,
					Text.translatable("flashbackinspector.log.empty"), this.width / 2, this.height / 2,
					0xFF9A93A6);
		}
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private static class OpeningList extends ElementListWidget<OpeningList.Entry> {
		OpeningList(MinecraftClient client, int width, int height, int y, int itemHeight) {
			super(client, width, height, y, itemHeight);
		}

		void addWidget(ClickableWidget widget) {
			addEntry(new Entry(widget));
		}

		@Override
		public int getRowWidth() {
			return ROW_WIDTH;
		}

		static class Entry extends ElementListWidget.Entry<Entry> {
			private final ClickableWidget widget;

			Entry(ClickableWidget widget) {
				this.widget = widget;
			}

			@Override
			public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
				int x = getContentX();
				int y = getContentY();
				int entryWidth = getContentWidth();
				widget.setX(x);
				widget.setY(y);
				widget.setWidth(entryWidth);
				widget.render(context, mouseX, mouseY, tickDelta);
			}

			@Override
			public List<? extends Element> children() {
				return List.of(widget);
			}

			@Override
			public List<? extends Selectable> selectableChildren() {
				return List.of(widget);
			}
		}
	}
}
