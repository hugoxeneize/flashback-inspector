package com.glamardor.flashbackinspector.gui;

import com.glamardor.flashbackinspector.config.InspectorConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The settings screen used when Cloth Config is not installed. Plain vanilla widgets, one scrolling
 * list, same options as the Cloth version.
 */
public class FallbackConfigScreen extends Screen {
	private static final int ROW_WIDTH = 310;

	@Nullable
	private final Screen parent;
	private final InspectorConfig config = InspectorConfig.get();

	public FallbackConfigScreen(@Nullable Screen parent) {
		super(Text.translatable("flashbackinspector.config.title"));
		this.parent = parent;
	}

	/** Leaves the replay visible behind the settings, the way the Cloth screen does. */
	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		if (this.client != null && this.client.world != null) {
			return;
		}
		super.renderBackground(context, mouseX, mouseY, delta);
	}

	@Override
	protected void init() {
		OptionList list = new OptionList(this.client, this.width, this.height - 96, 40, 25);

		list.addHeader(Text.translatable("flashbackinspector.category.recording"));
		list.addWidget(toggle("record_inventory", () -> config.recordInventory,
				value -> config.recordInventory = value));
		list.addWidget(toggle("record_containers", () -> config.recordContainers,
				value -> config.recordContainers = value));
		list.addWidget(new TicksSlider("scan_interval", config.scanIntervalTicks,
				value -> config.scanIntervalTicks = value));

		list.addHeader(Text.translatable("flashbackinspector.category.viewing"));
		list.addWidget(toggle("replace_inventory_key", () -> config.replaceInventoryKey,
				value -> config.replaceInventoryKey = value));
		list.addWidget(cycleButton("container_mode", () -> config.containerMode.getDisplayName(),
				() -> config.containerMode = config.containerMode.next()));
		list.addWidget(toggle("container_hint", () -> config.showContainerHint,
				value -> config.showContainerHint = value));
		list.addWidget(toggle("pause_when_opened", () -> config.pauseWhenOpened,
				value -> config.pauseWhenOpened = value));
		list.addWidget(toggle("show_timecode", () -> config.showTimecode,
				value -> config.showTimecode = value));

		addDrawableChild(list);

		addDrawableChild(ButtonWidget.builder(Text.translatable("flashbackinspector.config.reset"), button -> {
			config.resetToDefaults();
			clearAndInit();
		}).dimensions(this.width / 2 - 154, this.height - 30, 150, 20).build());

		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
				.dimensions(this.width / 2 + 4, this.height - 30, 150, 20)
				.build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 18, 0xFFFFFFFF);
		if (!ConfigScreenFactory.isClothPresent()) {
			context.drawCenteredTextWithShadow(this.textRenderer,
					Text.translatable("flashbackinspector.config.no_cloth"), this.width / 2,
					this.height - 44, 0xFF9A9A9A);
		}
	}

	@Override
	public void close() {
		config.save();
		MinecraftClient.getInstance().setScreen(parent);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private ClickableWidget toggle(String key, Supplier<Boolean> getter, Consumer<Boolean> setter) {
		return cycleButton(key,
				() -> getter.get() ? ScreenTexts.ON : ScreenTexts.OFF,
				() -> setter.accept(!getter.get()));
	}

	private ClickableWidget cycleButton(String key, Supplier<Text> value, Runnable onClick) {
		ButtonWidget button = ButtonWidget.builder(label(key, value.get()), b -> {
			onClick.run();
			b.setMessage(label(key, value.get()));
		}).dimensions(0, 0, ROW_WIDTH, 20).build();
		button.setTooltip(Tooltip.of(Text.translatable("flashbackinspector.option." + key + ".tooltip")));
		return button;
	}

	private static Text label(String key, Text value) {
		return Text.translatable("flashbackinspector.option." + key).append(": ").append(value);
	}

	/** Whole ticks, one to twenty. */
	private static class TicksSlider extends SliderWidget {
		private static final int MIN = 1;
		private static final int MAX = 20;
		private final String key;
		private final Consumer<Integer> setter;

		TicksSlider(String key, int current, Consumer<Integer> setter) {
			super(0, 0, ROW_WIDTH, 20, Text.empty(),
					MathHelper.clamp((current - MIN) / (float) (MAX - MIN), 0.0f, 1.0f));
			this.key = key;
			this.setter = setter;
			setTooltip(Tooltip.of(Text.translatable("flashbackinspector.option." + key + ".tooltip")));
			updateMessage();
		}

		private int currentValue() {
			return Math.round(MIN + (MAX - MIN) * (float) this.value);
		}

		@Override
		protected void updateMessage() {
			setMessage(label(key, Text.translatable("flashbackinspector.unit.ticks", currentValue())));
		}

		@Override
		protected void applyValue() {
			setter.accept(currentValue());
		}
	}

	private static class OptionList extends ElementListWidget<OptionList.Entry> {
		OptionList(MinecraftClient client, int width, int height, int y, int itemHeight) {
			super(client, width, height, y, itemHeight);
		}

		void addWidget(ClickableWidget widget) {
			addEntry(new WidgetEntry(widget));
		}

		void addHeader(Text text) {
			addEntry(new HeaderEntry(text));
		}

		@Override
		public int getRowWidth() {
			return ROW_WIDTH;
		}

		abstract static class Entry extends ElementListWidget.Entry<Entry> {
		}

		static class WidgetEntry extends Entry {
			private final ClickableWidget widget;

			WidgetEntry(ClickableWidget widget) {
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

		static class HeaderEntry extends Entry {
			private final Text text;

			HeaderEntry(Text text) {
				this.text = text;
			}

			@Override
			public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
				int x = getContentX();
				int y = getContentY();
				int entryWidth = getContentWidth();
				MinecraftClient client = MinecraftClient.getInstance();
				context.drawCenteredTextWithShadow(client.textRenderer, text,
						x + entryWidth / 2, y + 8, 0xFFE0C070);
			}

			@Override
			public List<? extends Element> children() {
				return List.of();
			}

			@Override
			public List<? extends Selectable> selectableChildren() {
				return List.of();
			}
		}
	}
}
