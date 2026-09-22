package com.glamardor.flashbackinspector.screen;

import com.glamardor.flashbackinspector.InventoryLayout;
import com.glamardor.flashbackinspector.config.InspectorConfig;
import com.glamardor.flashbackinspector.playback.InspectorState;
import com.moulberry.flashback.Flashback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The read-only inventory, as it stood on the tick currently on screen.
 *
 * <p>Two kinds of player end up here. The one who made the recording, whose whole inventory was
 * written into the file, and everybody else, of whom the file holds the worn items and the two
 * hands and nothing more — that is all a client is ever told about another player, so that is all
 * a recording of a client can contain.
 */
public class InspectorScreen extends Screen {
	private static final int SLOT = 18;
	private static final int PAD = 8;
	private static final int COLUMNS = 9;

	private static final int PANEL_BACKGROUND = 0xE8100E12;
	private static final int PANEL_BORDER = 0xFF4C4652;
	private static final int SLOT_BACKGROUND = 0xFF2B2830;
	private static final int SLOT_MISSING = 0xFF1A181D;

	@Nullable
	private final AbstractClientPlayerEntity target;
	private final boolean isOwner;

	private int left;
	private int top;
	private int panelWidth;
	private int panelHeight;
	private int containerTop;
	private int containerRows;
	private int equipmentTop;
	private int mainTop;
	private int hotbarTop;

	/** The stack under the mouse this frame, drawn last so its tooltip sits over everything. */
	@Nullable
	private ItemStack hovered;

	public InspectorScreen(@Nullable AbstractClientPlayerEntity target) {
		super(Text.translatable("flashbackinspector.screen.title"));
		this.target = target;
		this.isOwner = target != null && InspectorState.get().isOwner(target.getUuid());
	}

	/**
	 * Whose inventory the inventory key should show: whoever is being followed, and failing that
	 * the player who made the recording.
	 */
	@Nullable
	public static AbstractClientPlayerEntity defaultTarget() {
		AbstractClientPlayerEntity spectating = Flashback.getSpectatingPlayer();
		if (spectating != null) {
			return spectating;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		UUID owner = InspectorState.get().owner();
		if (owner != null && client.world != null
				&& client.world.getPlayerByUuid(owner) instanceof AbstractClientPlayerEntity player) {
			return player;
		}
		return null;
	}

	@Override
	protected void init() {
		InspectorState state = InspectorState.get();
		containerRows = isOwner && state.hasContainer()
				? Math.max(1, (state.containerSize() + COLUMNS - 1) / COLUMNS)
				: 0;

		panelWidth = COLUMNS * SLOT + PAD * 2;
		int y = PAD + 12;
		if (containerRows > 0) {
			y += 12;
			containerTop = y;
			y += containerRows * SLOT + PAD;
		}
		y += 12;
		equipmentTop = y;
		y += SLOT + PAD;
		mainTop = y;
		y += 3 * SLOT + 4;
		hotbarTop = y;
		y += SLOT + PAD;
		panelHeight = y + 24;

		left = (this.width - panelWidth) / 2;
		top = Math.max(4, (this.height - panelHeight) / 2);
		// Absolute positions from here on; the rows above were measured from the panel's corner.
		containerTop += top;
		equipmentTop += top;
		mainTop += top;
		hotbarTop += top;

		addDrawableChild(ButtonWidget.builder(Text.translatable("flashbackinspector.button.log"),
						button -> MinecraftClient.getInstance().setScreen(new ContainerLogScreen(this)))
				.dimensions(left + PAD, top + panelHeight - 22, panelWidth - PAD * 2, 18)
				.build());

		if (InspectorConfig.get().pauseWhenOpened) {
			InspectorState.pause();
		}
	}

	/**
	 * The panel itself goes in behind the widgets.
	 *
	 * <p>Drawn here rather than in {@code render}, because the log button is a child widget and
	 * children are drawn by {@code super.render} — a panel painted afterwards would cover it.
	 */
	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
		context.fill(left, top, left + panelWidth, top + panelHeight, PANEL_BACKGROUND);
		context.drawBorder(left, top, panelWidth, panelHeight, PANEL_BORDER);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		hovered = null;

		InspectorState state = InspectorState.get();
		int textLeft = left + PAD;

		context.drawTextWithShadow(this.textRenderer, headline(), textLeft, top + PAD, 0xFFFFFFFF);
		if (InspectorConfig.get().showTimecode) {
			int tick = InspectorState.currentTick();
			if (tick >= 0) {
				Text stamp = Text.literal(InspectorState.timecode(tick) + "  ·  " + tick);
				context.drawTextWithShadow(this.textRenderer, stamp,
						left + panelWidth - PAD - this.textRenderer.getWidth(stamp), top + PAD, 0xFF9A93A6);
			}
		}

		if (containerRows > 0) {
			context.drawTextWithShadow(this.textRenderer, containerHeading(state),
					textLeft, containerTop - 11, 0xFFE0C070);
			drawGrid(context, mouseX, mouseY, textLeft, containerTop, state.containerSize(),
					index -> state.container(index), true);
		}

		context.drawTextWithShadow(this.textRenderer,
				Text.translatable("flashbackinspector.section.inventory"), textLeft, equipmentTop - 11,
				0xFFE0C070);
		drawEquipmentRow(context, mouseX, mouseY, textLeft, equipmentTop);

		boolean legacyHotbar = !isOwner && hasLegacyHotbar();
		drawGrid(context, mouseX, mouseY, textLeft, mainTop, InventoryLayout.MAIN_SIZE,
				index -> isOwner ? state.inventory(InventoryLayout.MAIN_START + index) : ItemStack.EMPTY,
				isOwner);
		drawGrid(context, mouseX, mouseY, textLeft, hotbarTop, InventoryLayout.HOTBAR_SIZE,
				this::hotbar, isOwner || legacyHotbar);
		if (!isOwner) {
			context.drawCenteredTextWithShadow(this.textRenderer,
					Text.translatable(legacyHotbar
							? "flashbackinspector.not_recorded"
							: "flashbackinspector.not_sent").formatted(Formatting.GRAY),
					left + panelWidth / 2, mainTop + SLOT + 4, 0xFF8A8390);
		}

		if (hovered != null && !hovered.isEmpty()) {
			context.drawItemTooltip(this.textRenderer, hovered, mouseX, mouseY);
		}
	}

	/** Who is being looked at, and how much of them the file actually holds. */
	private Text headline() {
		if (target == null) {
			return Text.translatable("flashbackinspector.no_target").formatted(Formatting.GRAY);
		}
		Text who = Text.literal(target.getGameProfile().getName());
		String kind;
		if (isOwner) {
			kind = "flashbackinspector.source.recorder";
		} else if (hasLegacyHotbar()) {
			kind = "flashbackinspector.source.legacy";
		} else {
			kind = "flashbackinspector.source.equipment_only";
		}
		return Text.empty().append(who).append(Text.literal("  "))
				.append(Text.translatable(kind).formatted(Formatting.GRAY));
	}

	/**
	 * The hotbar, falling back to what Flashback itself recorded.
	 *
	 * <p>Flashback's own {@code recordHotbar} writes the nine slots as container packets, and on
	 * playback it puts them straight onto the player entity's inventory on the client. That is the
	 * one part of an inventory that exists in a replay made before this mod, so it is worth reading
	 * rather than drawing an empty row over the top of it.
	 */
	private ItemStack hotbar(int index) {
		if (isOwner) {
			ItemStack recorded = InspectorState.get().inventory(InventoryLayout.HOTBAR_START + index);
			if (!recorded.isEmpty()) {
				return recorded;
			}
		}
		return target == null ? ItemStack.EMPTY : ((PlayerEntity) target).getInventory().getStack(index);
	}

	/** Whether Flashback's own hotbar recording reached this player, which only the recorder's does. */
	private boolean hasLegacyHotbar() {
		if (target == null) {
			return false;
		}
		for (int i = 0; i < InventoryLayout.HOTBAR_SIZE; i++) {
			if (!((PlayerEntity) target).getInventory().getStack(i).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private Text containerHeading(InspectorState state) {
		Text title = state.containerTitle();
		BlockPos pos = state.containerPos();
		if (pos == null) {
			return title;
		}
		return Text.empty().append(title).append(Text.literal("  "))
				.append(Text.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ())
						.formatted(Formatting.DARK_GRAY));
	}

	/** The worn items and the off hand, in one row: head, chest, legs, feet, then the off hand. */
	private void drawEquipmentRow(DrawContext context, int mouseX, int mouseY, int x, int y) {
		for (int i = 0; i < InventoryLayout.ARMOUR.length; i++) {
			drawSlot(context, mouseX, mouseY, x + i * SLOT, y, equipment(InventoryLayout.ARMOUR[i]), true);
		}
		// A gap, then the off hand, so it reads as a hand rather than a fifth piece of armour.
		int offhandX = x + (InventoryLayout.ARMOUR.length + 1) * SLOT;
		drawSlot(context, mouseX, mouseY, offhandX, y, equipment(EquipmentSlot.OFFHAND), true);
	}

	/**
	 * One slot of worn equipment.
	 *
	 * <p>Read off the live entity rather than out of our own recording, and for everyone: Flashback
	 * already writes an equipment packet for every player it sees, so this works on the recording
	 * player too, and on replays made before this mod was ever installed.
	 */
	private ItemStack equipment(EquipmentSlot slot) {
		if (target == null) {
			return ItemStack.EMPTY;
		}
		if (isOwner) {
			int index = switch (slot) {
				case HEAD -> InventoryLayout.HEAD;
				case CHEST -> InventoryLayout.CHEST;
				case LEGS -> InventoryLayout.LEGS;
				case FEET -> InventoryLayout.FEET;
				case OFFHAND -> InventoryLayout.OFFHAND;
				default -> -1;
			};
			ItemStack recorded = InspectorState.get().inventory(index);
			if (!recorded.isEmpty()) {
				return recorded;
			}
		}
		return ((PlayerEntity) target).getEquippedStack(slot);
	}

	private interface SlotSource {
		ItemStack get(int index);
	}

	private void drawGrid(DrawContext context, int mouseX, int mouseY, int x, int y, int count,
			SlotSource source, boolean present) {
		for (int i = 0; i < count; i++) {
			int column = i % COLUMNS;
			int row = i / COLUMNS;
			drawSlot(context, mouseX, mouseY, x + column * SLOT, y + row * SLOT, source.get(i), present);
		}
	}

	private void drawSlot(DrawContext context, int mouseX, int mouseY, int x, int y, ItemStack stack,
			boolean present) {
		context.fill(x, y, x + SLOT - 2, y + SLOT - 2, present ? SLOT_BACKGROUND : SLOT_MISSING);
		if (!stack.isEmpty()) {
			context.drawItem(stack, x + 1, y + 1);
			context.drawStackOverlay(this.textRenderer, stack, x + 1, y + 1);
		}
		if (present && mouseX >= x && mouseX < x + SLOT - 2 && mouseY >= y && mouseY < y + SLOT - 2) {
			context.fill(x, y, x + SLOT - 2, y + SLOT - 2, 0x40FFFFFF);
			if (!stack.isEmpty()) {
				hovered = stack;
			}
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
