package me.dantaeusb.zetter.storage;

import me.dantaeusb.zetter.Zetter;
import me.dantaeusb.zetter.core.Helper;
import me.dantaeusb.zetter.canvastracker.CanvasServerTracker;
import me.dantaeusb.zetter.core.ZetterCanvasTypes;
import me.dantaeusb.zetter.core.ZetterRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.nio.ByteBuffer;

/**
 * It's not enough to just init data, we need to register it with
 * @see CanvasServerTracker ::registerCanvasData();
 */
public class CanvasData extends AbstractCanvasData {
    public static final String TYPE = "canvas";
    public static final String CODE_PREFIX = Zetter.MOD_ID + "_" + TYPE + "_";

    public static String getCanvasCode(int canvasId) {
        return CODE_PREFIX + canvasId;
    }

    protected CanvasData() {
        super(Zetter.MOD_ID, TYPE);
    }

    public boolean isRenderable() {
        return true;
    }

    public boolean isEditable() {
        return true;
    }

    /**
     * Create empty canvas data filled with canvas color
     * @param resolution
     * @param width
     * @param height
     * @return
     */
    public static CanvasData createFresh(Resolution resolution, int width, int height) {
        byte[] color = new byte[width * height * 4];
        ByteBuffer defaultColorBuffer = ByteBuffer.wrap(color);

        for (int x = 0; x < width * height; x++) {
            defaultColorBuffer.putInt(x * 4, Helper.CANVAS_COLOR);
        }

        final CanvasData newCanvas = new CanvasData();
        newCanvas.wrapData(resolution, width, height, color);

        return newCanvas;
    }

    /**
     * Create canvas from existing data
     * @param resolution
     * @param width
     * @param height
     * @param color
     * @return
     */
    public static CanvasData createWrap(Resolution resolution, int width, int height, byte[] color) {
        final CanvasData newCanvas = new CanvasData();
        newCanvas.wrapData(resolution, width, height, color);

        return newCanvas;
    }

    public CanvasDataType<CanvasData> getType() {
        return ZetterCanvasTypes.CANVAS.get();
    }

    public static CanvasData load(CompoundTag compoundTag) {
        final CanvasData newCanvas = new CanvasData();

        newCanvas.width = compoundTag.getInt(NBT_TAG_WIDTH);
        newCanvas.height = compoundTag.getInt(NBT_TAG_HEIGHT);

        if (compoundTag.contains(NBT_TAG_RESOLUTION)) {
            int resolutionOrdinal = compoundTag.getInt(NBT_TAG_RESOLUTION);
            newCanvas.resolution = Resolution.values()[resolutionOrdinal];
        } else {
            newCanvas.resolution = Helper.getResolution();
        }

        newCanvas.updateColorData(compoundTag.getByteArray(NBT_TAG_COLOR));

        return newCanvas;
    }

    public CompoundTag save(CompoundTag compoundTag) {
        return super.save(compoundTag);
    }

    /*
     * Networking
     */

    public static CanvasData readPacketData(FriendlyByteBuf networkBuffer) {
        final CanvasData newCanvas = new CanvasData();

        final byte resolutionOrdinal = networkBuffer.readByte();
        AbstractCanvasData.Resolution resolution = AbstractCanvasData.Resolution.values()[resolutionOrdinal];

        final int width = networkBuffer.readInt();
        final int height = networkBuffer.readInt();

        final int colorDataSize = networkBuffer.readInt();
        ByteBuffer colorData = networkBuffer.readBytes(colorDataSize).nioBuffer();
        byte[] unwrappedColorData = new byte[width * height * 4];
        colorData.get(unwrappedColorData);

        newCanvas.wrapData(
                resolution,
                width,
                height,
                unwrappedColorData
        );

        return newCanvas;
    }

    public static void writePacketData(CanvasData canvasData, FriendlyByteBuf networkBuffer) {
        networkBuffer.writeByte(canvasData.resolution.ordinal());
        networkBuffer.writeInt(canvasData.width);
        networkBuffer.writeInt(canvasData.height);
        networkBuffer.writeInt(canvasData.getColorDataBuffer().remaining());
        networkBuffer.writeBytes(canvasData.getColorDataBuffer());
    }
}

