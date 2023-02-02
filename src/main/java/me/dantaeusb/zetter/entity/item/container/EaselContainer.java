package me.dantaeusb.zetter.entity.item.container;

import me.dantaeusb.zetter.capability.canvastracker.CanvasTracker;
import me.dantaeusb.zetter.core.*;
import me.dantaeusb.zetter.entity.item.EaselEntity;
import com.google.common.collect.Lists;
import me.dantaeusb.zetter.item.CanvasItem;
import me.dantaeusb.zetter.network.packet.SEaselCanvasInitializationPacket;
import me.dantaeusb.zetter.network.packet.SEaselResetPacket;
import me.dantaeusb.zetter.storage.AbstractCanvasData;
import me.dantaeusb.zetter.storage.CanvasData;
import me.dantaeusb.zetter.storage.util.CanvasHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;


public class EaselContainer extends ItemStackHandler {
    public static final int STORAGE_SIZE = 2;
    public static final int CANVAS_SLOT = 0;
    public static final int PALETTE_SLOT = 1;

    /*
     * Canvas
     * Practically, this holder used to represent canvas
     * in Menu screen, because there's no data in item
     * on client side
     */
    private @Nullable CanvasHolder<CanvasData> canvas;

    /*
     * Entity and listeners
     */

    private EaselEntity easel;
    private List<ItemStackHandlerListener> listeners;

    public EaselContainer(EaselEntity easelEntity) {
        super(STORAGE_SIZE);

        this.easel = easelEntity;
    }

    @Deprecated
    public EaselContainer() {
        super(STORAGE_SIZE);
    }

    public void addListener(ItemStackHandlerListener listener) {
        if (this.listeners == null) {
            this.listeners = Lists.newArrayList();
        }

        this.listeners.add(listener);
    }

    public void removeListener(ItemStackHandlerListener listener) {
        this.listeners.remove(listener);
    }

    /*
     * Palette
     */

    public void damagePalette(int damage) {
        final int maxDamage = this.getPaletteStack().getMaxDamage() - 1;
        int newDamage = this.getPaletteStack().getDamageValue() + damage;
        newDamage = Math.min(newDamage, maxDamage);

        this.getPaletteStack().setDamageValue(newDamage);
    }

    /*
     * Canvas
     */

    public CanvasHolder<CanvasData> getCanvas() {
        return this.canvas;
    }

    /**
     * Check if we can draw on canvas or we should initialize
     * canvas data first
     * @return
     */
    public boolean isCanvasInitialized() {
        ItemStack canvasStack = this.getCanvasStack();

        if (canvasStack == null) {
            throw new IllegalStateException("Cannot check canvas initialization: no item in container");
        }

        String canvasCode = CanvasItem.getCanvasCode(canvasStack);
        return canvasCode != null;
    }

    /**
     * When canvas is empty, ask canvas item
     * to initialize data before start drawing
     *
     * Server-only
     *
     * @return boolean True if initialization is successful
     */
    public boolean initializeCanvas() {
        ItemStack canvasStack = this.getCanvasStack();

        if (canvasStack == null) {
            throw new IllegalStateException("Cannot initialize canvas: no item in container");
        }

        String canvasCode = CanvasItem.getCanvasCode(canvasStack);

        if (canvasCode != null) {
            // Already
            return false;
        }

        int resolution = CanvasItem.getResolution(canvasStack);
        int[] size = CanvasItem.getBlockSize(canvasStack);

        if (size == null || size.length != 2) {
            throw new IllegalArgumentException("Cannot initialize canvas: can't read item size");
        }

        // @todo: Incorrect size, not reading original size
        CanvasItem.createEmpty(canvasStack, AbstractCanvasData.Resolution.get(resolution), size[0], size[1], this.easel.getLevel());
        canvasCode = CanvasItem.getCanvasCode(canvasStack);

        SEaselCanvasInitializationPacket initPacket = new SEaselCanvasInitializationPacket(this.easel.getId(), canvasCode);

        for (Player player : this.easel.getPlayersUsing()) {
            ZetterNetwork.simpleChannel.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) player), initPacket);
        }

        this.onContentsChanged(CANVAS_SLOT);
        this.handleCanvasChange(canvasCode);

        return true;
    }

    /**
     * @todo: [MED] Just sync item?
     * @param canvasCode
     */
    public void handleCanvasChange(@Nullable String canvasCode) {
        if (canvasCode == null || canvasCode.equals(CanvasData.getCanvasCode(0))) {
            this.canvas = null;
            return;
        }

        CanvasTracker canvasTracker;

        if (this.easel.getLevel().isClientSide()) {
            canvasTracker = this.easel.getLevel().getCapability(ZetterCapabilities.CANVAS_TRACKER).orElse(null);
        } else {
            canvasTracker = this.easel.getLevel().getServer().overworld().getCapability(ZetterCapabilities.CANVAS_TRACKER).orElse(null);
        }

        CanvasData canvas = canvasTracker.getCanvasData(canvasCode);

        if (canvas == null) {
            this.canvas = null;
            return;
        }

        this.canvas = new CanvasHolder<>(canvasCode, canvas);
    }

    /*
     * Validity
     */

    /**
     * @return
     */
    public boolean stillValid(Player player) {
        if (this.easel != null && this.easel.isAlive()) {
            return player.distanceToSqr((double)this.easel.getPos().getX() + 0.5D, (double)this.easel.getPos().getY() + 0.5D, (double)this.easel.getPos().getZ() + 0.5D) <= 64.0D;
        }

        return false;
    }

    public boolean isItemValid(int index, ItemStack stack) {
        if (index == 0 && stack.getItem() == ZetterItems.CANVAS.get()) {
            int[] canvasSize = CanvasItem.getBlockSize(stack);
            assert canvasSize != null;

            return canvasSize[0] <= 2 && canvasSize[1] <= 2;
        }

        return index == 1 && stack.getItem() == ZetterItems.PALETTE.get();
    }

    /*
     * Getter-setters
     */
    public ItemStack getCanvasStack() {
        return this.getStackInSlot(CANVAS_SLOT);
    }

    public ItemStack getPaletteStack() {
        return this.getStackInSlot(PALETTE_SLOT);
    }

    public ItemStack extractCanvasStack() {
        return this.extractItem(CANVAS_SLOT, this.getSlotLimit(CANVAS_SLOT), false);
    }

    public ItemStack extractPaletteStack() {
        return this.extractItem(PALETTE_SLOT, this.getSlotLimit(PALETTE_SLOT), false);
    }

    @Override
    public int getSlotLimit(int slot)
    {
        return 1;
    }

    public void setCanvasStack(ItemStack canvasStack) {
        this.setStackInSlot(CANVAS_SLOT, canvasStack);
    }

    public void setPaletteStack(ItemStack canvasStack) {
        this.setStackInSlot(PALETTE_SLOT, canvasStack);
    }

    public void changed() {
        this.onContentsChanged(0);
    }

    @Override
    protected void onLoad() {
        this.handleCanvasChange(CanvasItem.getCanvasCode(this.getCanvasStack()));
    }

    @Override
    protected void onContentsChanged(int slot)
    {
        if (this.listeners != null) {
            for(ItemStackHandlerListener listener : this.listeners) {
                listener.containerChanged(this, slot);
            }
        }

        if (slot == CANVAS_SLOT) {
            ItemStack canvasStack = this.getCanvasStack();

            if (canvasStack.isEmpty()) {
                this.handleCanvasChange(null);
                return;
            }

            String canvasCode = CanvasItem.getCanvasCode(canvasStack);

            if (canvasCode == null) {
                int[] size = CanvasItem.getBlockSize(canvasStack);

                if (size == null || size.length != 2) {
                    this.handleCanvasChange(null);
                    return;
                }

                canvasCode = CanvasData.getDefaultCanvasCode(size[0], size[1]);
            }

            this.handleCanvasChange(canvasCode);
        }
    }
}