package com.glamardor.flashbackinspector.playback;

import com.glamardor.flashbackinspector.InventoryLayout;
import com.glamardor.flashbackinspector.net.InspectorPayloads;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The viewing half: what the recording player was carrying at the tick currently on screen.
 *
 * <p>Rebuilt as the replay plays, from the same packets the recorder wrote. Seeking is handled for
 * free: Flashback replays from the nearest snapshot, and the snapshot carries a full state.
 */
public final class InspectorState {
    private static final InspectorState INSTANCE = new InspectorState();

    private UUID owner;
    private final ItemStack[] inventory = new ItemStack[InventoryLayout.SIZE];

    private int containerId = -1;
    private Text containerTitle = Text.empty();
    private ItemStack[] containerContents = new ItemStack[0];
    private BlockPos containerPos;

    /** Every container opening seen so far, keyed by the replay tick it happened on. */
    private final TreeMap<Integer, ContainerOpening> openings = new TreeMap<>();

    /** An opening, as shown in the log. */
    public record ContainerOpening(int tick, Text title, @Nullable BlockPos pos, int size) {
    }

    private InspectorState() {
        clear();
    }

    public static InspectorState get() {
        return INSTANCE;
    }

    public void clear() {
        owner = null;
        java.util.Arrays.fill(inventory, ItemStack.EMPTY);
        containerId = -1;
        containerTitle = Text.empty();
        containerContents = new ItemStack[0];
        containerPos = null;
        openings.clear();
    }

    // --- reading ---

    public boolean hasData() {
        return owner != null;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public boolean isOwner(UUID uuid) {
        return owner != null && owner.equals(uuid);
    }

    public ItemStack inventory(int index) {
        return index >= 0 && index < inventory.length ? inventory[index] : ItemStack.EMPTY;
    }

    public boolean hasContainer() {
        return containerId != -1;
    }

    /** Whether the open "container" is really the player's own screen, with its crafting grid. */
    public boolean isPlayerScreen() {
        return containerId == InspectorPayloads.PLAYER_SCREEN_ID;
    }

    public Text containerTitle() {
        return containerTitle;
    }

    public int containerSize() {
        return containerContents.length;
    }

    public ItemStack container(int index) {
        return index >= 0 && index < containerContents.length ? containerContents[index] : ItemStack.EMPTY;
    }

    @Nullable
    public BlockPos containerPos() {
        return containerPos;
    }

    public List<ContainerOpening> openings() {
        return new ArrayList<>(openings.values());
    }

    public boolean hasOpenings() {
        return !openings.isEmpty();
    }

    // --- writing, from the packet receivers ---

    public void applySnapshot(InspectorPayloads.Snapshot snapshot) {
        owner = snapshot.owner();
        List<ItemStack> stacks = snapshot.inventory();
        for (int i = 0; i < inventory.length; i++) {
            inventory[i] = i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY;
        }
        snapshot.container().ifPresentOrElse(this::openContainer, this::closeContainer);
    }

    public void applySlots(InspectorPayloads.Slots slots) {
        if (slots.containerId() == 0) {
            for (InspectorPayloads.SlotChange change : slots.changes()) {
                if (change.slot() >= 0 && change.slot() < inventory.length) {
                    inventory[change.slot()] = change.stack();
                }
            }
            return;
        }
        if (slots.containerId() != containerId) {
            // A delta for a container we never saw opened, which happens when the replay is opened
            // partway through. Nothing sensible to apply it to; the next snapshot will sort it out.
            return;
        }
        for (InspectorPayloads.SlotChange change : slots.changes()) {
            if (change.slot() >= 0 && change.slot() < containerContents.length) {
                containerContents[change.slot()] = change.stack();
            }
        }
    }

    public void applyContainerEvent(InspectorPayloads.ContainerEvent event) {
        if (event.opened()) {
            openContainer(event.view());
            int id = event.view().containerId();
            // The player's own screen drives the follow mode but is not a chest for the log.
            if (id > 0 && id != InspectorPayloads.PLAYER_SCREEN_ID) {
                record(event.view());
            }
        } else {
            closeContainer();
        }
    }

    private void openContainer(InspectorPayloads.ContainerView view) {
        containerId = view.containerId();
        containerTitle = view.title();
        containerContents = view.contents().toArray(new ItemStack[0]);
        containerPos = view.pos().orElse(null);
    }

    private void closeContainer() {
        containerId = -1;
        containerTitle = Text.empty();
        containerContents = new ItemStack[0];
        containerPos = null;
    }

    /**
     * Adds an opening to the log.
     *
     * <p>Keyed by tick, so watching the same stretch twice, which happens on every seek, leaves
     * one entry rather than one per pass.
     */
    private void record(InspectorPayloads.ContainerView view) {
        int tick = currentTick();
        if (tick < 0) {
            return;
        }
        openings.put(tick, new ContainerOpening(tick, view.title(), view.pos().orElse(null),
                view.contents().size()));
    }

    /** The replay tick on screen, or -1 when this is not a replay. */
    public static int currentTick() {
        ReplayServer server = replayServer();
        return server == null ? -1 : server.getReplayTick();
    }

    public static int totalTicks() {
        ReplayServer server = replayServer();
        return server == null ? 0 : server.getTotalReplayTicks();
    }

    /** Moves the replay to a tick and stops it there. */
    public static void seek(int tick) {
        ReplayServer server = replayServer();
        if (server != null) {
            server.replayPaused = true;
            server.goToReplayTick(tick);
        }
    }

    public static void pause() {
        ReplayServer server = replayServer();
        if (server != null) {
            server.replayPaused = true;
        }
    }

    @Nullable
    public static ReplayServer replayServer() {
        return MinecraftClient.getInstance().getServer() instanceof ReplayServer server ? server : null;
    }

    /** A tick count as {@code h:mm:ss}, the way Flashback's own timeline reads. */
    public static String timecode(int tick) {
        int seconds = tick / 20;
        return String.format("%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }
}
