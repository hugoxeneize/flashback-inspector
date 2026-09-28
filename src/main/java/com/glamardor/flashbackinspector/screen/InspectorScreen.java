package com.glamardor.flashbackinspector.screen;

import com.glamardor.flashbackinspector.InventoryLayout;
import com.glamardor.flashbackinspector.config.InspectorConfig;
import com.glamardor.flashbackinspector.playback.InspectorState;
import com.moulberry.flashback.Flashback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The read-only inventory, drawn with the vanilla textures and slot positions.
 *
 * <p>Two kinds of player end up here. The one who made the recording, whose whole inventory was
 * written into the file, and everybody else, of whom the file holds the worn items and the two
 * hands and nothing more.
 */
public class InspectorScreen extends Screen {
    private static final Identifier INVENTORY_TEXTURE =
            Identifier.ofVanilla("textures/gui/container/inventory.png");
    private static final Identifier CHEST_TEXTURE =
            Identifier.ofVanilla("textures/gui/container/generic_54.png");

    private static final int PANEL_WIDTH = 176;
    private static final int INVENTORY_HEIGHT = 166;
    private static final int LINE = 11;
    private static final int TEXT_DARK = 0xFF404040;
    private static final int MUTED = 0xFFA0A0A0;

    /**
     * A container with a background and slot positions of its own, chosen by its handler type.
     * A negative titleX centres the title, the way vanilla does for furnaces and dispensers.
     */
    private record Special(Identifier texture, int height, int titleX, int titleY, int[][] slots,
            int mainY, int hotbarY) {
    }

    @Nullable
    private final AbstractClientPlayerEntity target;
    private final boolean isOwner;
    /** Whether this screen was asked for, as opposed to appearing on its own. */
    private final boolean requested;
    private boolean pauseApplied;

    private int left;
    private int top;
    private int panelHeight;
    /** Rows of the generic container grid, or zero when it is not one. */
    private int rows;
    @Nullable
    private Special special;
    private int laidOutContainerSize = -1;
    private String laidOutType = "";

    @Nullable
    private ItemStack hovered;

    /** Opened on purpose, by the inventory key. May pause the replay, if that is set. */
    public InspectorScreen(@Nullable AbstractClientPlayerEntity target) {
        this(target, true);
    }

    /** Opened by the follow mode. Never pauses, or the replay would stop on every container. */
    public static InspectorScreen followed(@Nullable AbstractClientPlayerEntity target) {
        return new InspectorScreen(target, false);
    }

    private InspectorScreen(@Nullable AbstractClientPlayerEntity target, boolean requested) {
        super(Text.translatable("flashbackinspector.screen.title"));
        this.target = target;
        this.requested = requested;
        this.isOwner = target != null && InspectorState.get().isOwner(target.getUuid());
    }

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

    /** Size of a real container grid; zero for no container or for the player's own screen. */
    private int chestSize(InspectorState state) {
        return isOwner && state.hasContainer() && !state.isPlayerScreen() ? state.containerSize() : 0;
    }

    private boolean inContainer() {
        return rows > 0 || special != null;
    }

    @Override
    protected void init() {
        InspectorState state = InspectorState.get();
        int containerSize = chestSize(state);
        laidOutContainerSize = containerSize;
        laidOutType = state.containerType();

        special = containerSize > 0 ? specialFor(laidOutType, containerSize) : null;
        if (special != null) {
            rows = 0;
            panelHeight = special.height();
        } else {
            rows = containerSize > 0 ? Math.min(6, (containerSize + 8) / 9) : 0;
            panelHeight = rows > 0 ? 114 + rows * 18 : INVENTORY_HEIGHT;
        }

        left = (this.width - PANEL_WIDTH) / 2;
        // Room above for the name line, and below for the log button.
        top = Math.max(30, (this.height - panelHeight) / 2);

        addDrawableChild(ButtonWidget.builder(Text.translatable("flashbackinspector.button.log"),
                        button -> MinecraftClient.getInstance().setScreen(new ContainerLogScreen(this)))
                .dimensions(left, top + panelHeight + 4, PANEL_WIDTH, 20)
                .build());

        if (requested && !pauseApplied && InspectorConfig.get().pauseWhenOpened) {
            pauseApplied = true;
            InspectorState.pause();
        }
    }

    /** The vanilla background goes in behind the widgets, so the log button stays on top. */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        if (special != null) {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, special.texture(), left, top,
                    0f, 0f, PANEL_WIDTH, special.height(), 256, 256);
        } else if (rows > 0) {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, CHEST_TEXTURE, left, top,
                    0f, 0f, PANEL_WIDTH, rows * 18 + 17, 256, 256);
            context.drawTexture(RenderPipelines.GUI_TEXTURED, CHEST_TEXTURE, left, top + rows * 18 + 17,
                    0f, 126f, PANEL_WIDTH, 96, 256, 256);
        } else {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, INVENTORY_TEXTURE, left, top,
                    0f, 0f, PANEL_WIDTH, INVENTORY_HEIGHT, 256, 256);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        InspectorState state = InspectorState.get();

        // A container can open, close, change size or turn out to have a known type while this
        // screen is up. Re-measure rather than draw a layout into space reserved for another.
        int containerSize = chestSize(state);
        if (containerSize != laidOutContainerSize || !state.containerType().equals(laidOutType)) {
            clearAndInit();
            return;
        }

        super.render(context, mouseX, mouseY, delta);
        hovered = null;

        drawHeader(context, state);

        if (special != null) {
            drawSpecialLayout(context, mouseX, mouseY, state);
        } else if (rows > 0) {
            drawContainerLayout(context, mouseX, mouseY, state, containerSize);
        } else {
            drawInventoryLayout(context, mouseX, mouseY, state);
        }

        if (hovered != null && !hovered.isEmpty()) {
            context.drawItemTooltip(this.textRenderer, hovered, mouseX, mouseY);
        }
    }

    // --- layouts, at the vanilla slot positions ---

    private void drawInventoryLayout(DrawContext context, int mouseX, int mouseY, InspectorState state) {
        context.drawText(this.textRenderer, Text.translatable("container.crafting"),
                left + 97, top + 8, TEXT_DARK, false);

        if (target != null) {
            InventoryScreen.drawEntity(context, left + 26, top + 8, left + 75, top + 78, 30, 0.0625f,
                    (float) mouseX, (float) mouseY, target);
        }

        // Armour down the left, head to feet, then the off hand.
        for (int i = 0; i < InventoryLayout.ARMOUR.length; i++) {
            drawSlot(context, mouseX, mouseY, left + 8, top + 8 + i * 18,
                    equipment(InventoryLayout.ARMOUR[i]), true);
        }
        drawSlot(context, mouseX, mouseY, left + 77, top + 62, equipment(EquipmentSlot.OFFHAND), true);

        if (isOwner && state.isPlayerScreen()) {
            // Vanilla positions: the 2x2 grid, then the result slot to its right.
            for (int i = 0; i < 4; i++) {
                drawSlot(context, mouseX, mouseY, left + 98 + (i % 2) * 18, top + 18 + (i / 2) * 18,
                        state.container(1 + i), true);
            }
            drawSlot(context, mouseX, mouseY, left + 154, top + 28, state.container(0), true);
        }

        drawMainAndHotbar(context, mouseX, mouseY, state, 84, 142);
    }

    private void drawContainerLayout(DrawContext context, int mouseX, int mouseY, InspectorState state,
            int containerSize) {
        context.drawText(this.textRenderer, state.containerTitle(), left + 8, top + 6, TEXT_DARK, false);
        context.drawText(this.textRenderer, Text.translatable("container.inventory"),
                left + 8, top + 20 + rows * 18, TEXT_DARK, false);

        int shown = Math.min(containerSize, rows * 9);
        for (int i = 0; i < shown; i++) {
            drawSlot(context, mouseX, mouseY, left + 8 + (i % 9) * 18, top + 18 + (i / 9) * 18,
                    state.container(i), true);
        }

        drawMainAndHotbar(context, mouseX, mouseY, state, 31 + rows * 18, 89 + rows * 18);
    }

    /** Machines and workstations: their own texture and slot spots. */
    private void drawSpecialLayout(DrawContext context, int mouseX, int mouseY, InspectorState state) {
        Special layout = special;
        Text title = state.containerTitle();
        int titleX = layout.titleX() < 0
                ? (PANEL_WIDTH - this.textRenderer.getWidth(title)) / 2
                : layout.titleX();
        context.drawText(this.textRenderer, title, left + titleX, top + layout.titleY(), TEXT_DARK, false);
        context.drawText(this.textRenderer, Text.translatable("container.inventory"),
                left + 8, top + layout.mainY() - 12, TEXT_DARK, false);

        for (int i = 0; i < layout.slots().length; i++) {
            int[] at = layout.slots()[i];
            drawSlot(context, mouseX, mouseY, left + at[0], top + at[1], state.container(i), true);
        }

        drawMainAndHotbar(context, mouseX, mouseY, state, layout.mainY(), layout.hotbarY());
    }

    private void drawMainAndHotbar(DrawContext context, int mouseX, int mouseY, InspectorState state,
            int mainY, int hotbarY) {
        for (int i = 0; i < InventoryLayout.MAIN_SIZE; i++) {
            ItemStack stack = isOwner ? state.inventory(InventoryLayout.MAIN_START + i) : ItemStack.EMPTY;
            drawSlot(context, mouseX, mouseY, left + 8 + (i % 9) * 18, top + mainY + (i / 9) * 18,
                    stack, isOwner);
        }
        boolean legacyHotbar = !isOwner && hasLegacyHotbar();
        for (int i = 0; i < InventoryLayout.HOTBAR_SIZE; i++) {
            drawSlot(context, mouseX, mouseY, left + 8 + i * 18, top + hotbarY, hotbar(i),
                    isOwner || legacyHotbar);
        }

        if (!isOwner) {
            // Grey out what the file does not hold, and say why.
            context.fill(left + 7, top + mainY - 1, left + 169, top + mainY + 3 * 18 - 1, 0xD0101010);
            if (!legacyHotbar) {
                context.fill(left + 7, top + hotbarY - 1, left + 169, top + hotbarY + 17, 0xD0101010);
            }
            drawNotice(context, mainY, legacyHotbar
                    ? "flashbackinspector.not_recorded"
                    : "flashbackinspector.not_sent");
        }
    }

    private void drawNotice(DrawContext context, int mainY, String key) {
        List<OrderedText> lines = this.textRenderer.wrapLines(
                Text.translatable(key).formatted(Formatting.GRAY), PANEL_WIDTH - 32);
        int y = top + mainY + (3 * 18 - lines.size() * LINE) / 2;
        for (OrderedText line : lines) {
            context.drawTextWithShadow(this.textRenderer, line,
                    left + (PANEL_WIDTH - this.textRenderer.getWidth(line)) / 2, y, MUTED);
            y += LINE;
        }
    }

    /** Name and timecode above the panel, then where the data came from. */
    private void drawHeader(DrawContext context, InspectorState state) {
        int y = top - 24;
        int right = left + PANEL_WIDTH;

        int stampWidth = 0;
        if (InspectorConfig.get().showTimecode) {
            int tick = InspectorState.currentTick();
            if (tick >= 0) {
                Text stamp = Text.literal(InspectorState.timecode(tick) + "  ·  " + tick);
                stampWidth = this.textRenderer.getWidth(stamp) + 8;
                context.drawTextWithShadow(this.textRenderer, stamp,
                        right - this.textRenderer.getWidth(stamp), y, MUTED);
            }
        }

        Text name = target == null
                ? Text.translatable("flashbackinspector.no_target").formatted(Formatting.GRAY)
                : Text.literal(target.getGameProfile().name());
        int room = PANEL_WIDTH - stampWidth;
        if (this.textRenderer.getWidth(name) > room) {
            name = Text.literal(this.textRenderer.trimToWidth(name.getString(), room))
                    .setStyle(name.getStyle());
        }
        context.drawTextWithShadow(this.textRenderer, name, left, y, 0xFFFFFFFF);

        if (target != null) {
            String source;
            if (isOwner) {
                source = "flashbackinspector.source.recorder";
            } else if (hasLegacyHotbar()) {
                source = "flashbackinspector.source.legacy";
            } else {
                source = "flashbackinspector.source.equipment_only";
            }
            Text line = Text.translatable(source);
            BlockPos pos = inContainer() ? state.containerPos() : null;
            if (pos != null) {
                line = Text.empty().append(line)
                        .append(Text.literal("  ·  " + pos.getX() + " " + pos.getY() + " " + pos.getZ()));
            }
            context.drawTextWithShadow(this.textRenderer, line, left, y + LINE, MUTED);
        }
    }

    // --- per-type layouts ---

    /**
     * The layout for a handler type, or null for anything without one of its own.
     *
     * <p>A layout is only used when its slot count matches what was recorded, so a plugin menu that
     * reuses a vanilla type with a different shape falls back to the plain grid.
     */
    @Nullable
    private static Special specialFor(String type, int size) {
        Special layout = switch (type) {
            case "minecraft:crafting" -> new Special(gui("crafting_table"), 166, 29, 6, craftingSlots(), 84, 142);
            case "minecraft:furnace" -> new Special(gui("furnace"), 166, -1, 6, furnaceSlots(), 84, 142);
            case "minecraft:blast_furnace" -> new Special(gui("blast_furnace"), 166, -1, 6, furnaceSlots(), 84, 142);
            case "minecraft:smoker" -> new Special(gui("smoker"), 166, -1, 6, furnaceSlots(), 84, 142);
            case "minecraft:hopper" -> new Special(gui("hopper"), 133, 8, 6, hopperSlots(), 51, 109);
            case "minecraft:generic_3x3" -> new Special(gui("dispenser"), 166, -1, 6, dispenserSlots(), 84, 142);
            case "minecraft:brewing_stand" -> new Special(gui("brewing_stand"), 166, -1, 6, brewingSlots(), 84, 142);
            case "minecraft:enchantment" -> new Special(gui("enchanting_table"), 166, 12, 5,
                    new int[][] {{15, 47}, {35, 47}}, 84, 142);
            case "minecraft:anvil" -> new Special(gui("anvil"), 166, 60, 6,
                    new int[][] {{27, 47}, {76, 47}, {134, 47}}, 84, 142);
            case "minecraft:smithing" -> new Special(gui("smithing"), 166, 44, 15,
                    new int[][] {{8, 48}, {26, 48}, {44, 48}, {98, 48}}, 84, 142);
            case "minecraft:grindstone" -> new Special(gui("grindstone"), 166, 8, 6,
                    new int[][] {{49, 19}, {49, 40}, {129, 34}}, 84, 142);
            case "minecraft:stonecutter" -> new Special(gui("stonecutter"), 166, 8, 4,
                    new int[][] {{20, 33}, {143, 33}}, 84, 142);
            default -> null;
        };
        return layout != null && layout.slots().length == size ? layout : null;
    }

    private static Identifier gui(String name) {
        return Identifier.ofVanilla("textures/gui/container/" + name + ".png");
    }

    /** Slot 0 is the result, slots 1 to 9 the 3x3 grid. */
    private static int[][] craftingSlots() {
        int[][] slots = new int[10][];
        slots[0] = new int[] {124, 35};
        for (int i = 0; i < 9; i++) {
            slots[i + 1] = new int[] {30 + (i % 3) * 18, 17 + (i / 3) * 18};
        }
        return slots;
    }

    /** Input, fuel, output. */
    private static int[][] furnaceSlots() {
        return new int[][] {{56, 17}, {56, 53}, {116, 35}};
    }

    private static int[][] hopperSlots() {
        int[][] slots = new int[5][];
        for (int i = 0; i < 5; i++) {
            slots[i] = new int[] {44 + i * 18, 20};
        }
        return slots;
    }

    private static int[][] dispenserSlots() {
        int[][] slots = new int[9][];
        for (int i = 0; i < 9; i++) {
            slots[i] = new int[] {62 + (i % 3) * 18, 17 + (i / 3) * 18};
        }
        return slots;
    }

    /** Three bottles (left, bottom, right), then the ingredient, then the blaze powder. */
    private static int[][] brewingSlots() {
        return new int[][] {{56, 51}, {79, 58}, {102, 51}, {79, 17}, {17, 17}};
    }

    // --- what goes in the slots ---

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

    private ItemStack hotbar(int index) {
        if (isOwner) {
            ItemStack recorded = InspectorState.get().inventory(InventoryLayout.HOTBAR_START + index);
            if (!recorded.isEmpty()) {
                return recorded;
            }
        }
        return target == null ? ItemStack.EMPTY : ((PlayerEntity) target).getInventory().getStack(index);
    }

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

    /** One slot: the item, and the vanilla white highlight when the mouse is over it. */
    private void drawSlot(DrawContext context, int mouseX, int mouseY, int x, int y, ItemStack stack,
            boolean active) {
        if (!stack.isEmpty()) {
            context.drawItem(stack, x, y);
            context.drawStackOverlay(this.textRenderer, stack, x, y);
        }
        if (active && mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
            context.fill(x, y, x + 16, y + 16, 0x80FFFFFF);
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
