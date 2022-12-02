package me.dantaeusb.zetter.storage;

import me.dantaeusb.zetter.Zetter;
import me.dantaeusb.zetter.canvastracker.CanvasServerTracker;
import me.dantaeusb.zetter.core.Helper;
import me.dantaeusb.zetter.core.ZetterCanvasTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * It's not enough to just init data, we need to register it with
 * @see CanvasServerTracker ::registerCanvasData();
 */
public class PaintingData extends AbstractCanvasData {
    public static final String TYPE = "painting";
    public static final String CODE_PREFIX = Zetter.MOD_ID + "_" + TYPE + "_";

    public static final CanvasDataBuilder<PaintingData> BUILDER = new PaintingDataBuilder();

    public static final int MAX_GENERATION = 2;

    protected static final String NBT_TAG_AUTHOR_NAME = "author_name";
    protected static final String NBT_TAG_AUTHOR_UUID = "AuthorUuid";
    protected static final String NBT_TAG_TITLE = "title";
    protected static final String NBT_TAG_BANNED = "Banned";

    protected UUID authorUuid;
    protected String authorName;
    protected String title;
    protected boolean banned = false;

    protected PaintingData() {}

    public static String getCanvasCode(int canvasId) {
        return CODE_PREFIX + canvasId;
    }

    public void setMetaProperties(UUID authorUuid, String authorName, String title) {
        this.authorUuid = authorUuid;
        this.authorName = authorName;
        this.title = title;
    }

    public String getPaintingTitle() {
        return this.title;
    }

    public UUID getAuthorUuid() {
        return this.authorUuid;
    }

    public String getAuthorName() {
        return this.authorName;
    }

    public static String getPaintingCode(int paintingId) {
        return CODE_PREFIX + paintingId;
    }

    public boolean isRenderable() {
        return true;
    }

    public boolean isEditable() {
        return false;
    }

    public CanvasDataType<? extends PaintingData> getType() {
        return ZetterCanvasTypes.PAINTING.get();
    }

    @Override
    public void correctData(ServerLevel level) {
        if (this.authorUuid == null) {
            UUID authorUuid = Helper.tryToRestoreAuthorUuid(level, this.authorName);

            if (authorUuid != null) {
                this.authorUuid = authorUuid;
                this.setDirty();
            } else {
                Zetter.LOG.warn("Cannot restore author UUID for player " + this.authorName);
            }
        }
    }

    public CompoundTag save(CompoundTag compoundTag) {
        super.save(compoundTag);

        compoundTag.putUUID(NBT_TAG_AUTHOR_UUID, this.authorUuid);
        compoundTag.putString(NBT_TAG_AUTHOR_NAME, this.authorName);
        compoundTag.putString(NBT_TAG_TITLE, this.title);
        compoundTag.putBoolean(NBT_TAG_BANNED, this.banned);

        return compoundTag;
    }

    private static class PaintingDataBuilder implements CanvasDataBuilder<PaintingData> {

        /**
         * @todo: [HIGH] Use placeholders
         * @param resolution
         * @param width
         * @param height
         * @return
         */
        public PaintingData createFresh(Resolution resolution, int width, int height) {
            final PaintingData newPainting = new PaintingData();

            byte[] color = new byte[width * height * 4];
            ByteBuffer defaultColorBuffer = ByteBuffer.wrap(color);

            for (int x = 0; x < width * height; x++) {
                defaultColorBuffer.putInt(x * 4, Helper.CANVAS_COLOR);
            }

            newPainting.wrapData(resolution, width, height, color);

            return newPainting;
        }

        public PaintingData createWrap(Resolution resolution, int width, int height, byte[] color) {
            final PaintingData newPainting = new PaintingData();
            newPainting.wrapData(resolution, width, height, color);

            return newPainting;
        }

        /*
         * Serialization
         */

        public PaintingData load(CompoundTag compoundTag) {
            final PaintingData newPainting = new PaintingData();

            newPainting.width = compoundTag.getInt(NBT_TAG_WIDTH);
            newPainting.height = compoundTag.getInt(NBT_TAG_HEIGHT);

            int resolutionOrdinal = compoundTag.getInt(NBT_TAG_RESOLUTION);
            newPainting.resolution = Resolution.values()[resolutionOrdinal];

            newPainting.updateColorData(compoundTag.getByteArray(NBT_TAG_COLOR));

            if (compoundTag.contains(NBT_TAG_AUTHOR_UUID)) {
                newPainting.authorUuid = compoundTag.getUUID(NBT_TAG_AUTHOR_UUID);
            } else {
                newPainting.authorUuid = null;
            }

            newPainting.authorName = compoundTag.getString(NBT_TAG_AUTHOR_NAME);
            newPainting.title = compoundTag.getString(NBT_TAG_TITLE);
            newPainting.banned = compoundTag.getBoolean(NBT_TAG_BANNED);

            return newPainting;
        }

        /*
         * Networking
         */

        public PaintingData readPacketData(FriendlyByteBuf networkBuffer) {
            final PaintingData newPainting = new PaintingData();

            final byte resolutionOrdinal = networkBuffer.readByte();
            AbstractCanvasData.Resolution resolution = AbstractCanvasData.Resolution.values()[resolutionOrdinal];

            final int width = networkBuffer.readInt();
            final int height = networkBuffer.readInt();

            final int colorDataSize = networkBuffer.readInt();
            ByteBuffer colorData = networkBuffer.readBytes(colorDataSize).nioBuffer();
            byte[] unwrappedColorData = new byte[width * height * 4];
            colorData.get(unwrappedColorData);

            newPainting.wrapData(
                resolution,
                width,
                height,
                unwrappedColorData
            );

            final UUID authorUuid = networkBuffer.readUUID();
            final String authorName = networkBuffer.readUtf(64);
            final String title = networkBuffer.readUtf(32);

            newPainting.setMetaProperties(
                authorUuid,
                authorName,
                title
            );

            return newPainting;
        }

        public void writePacketData(PaintingData canvasData, FriendlyByteBuf networkBuffer) {
            networkBuffer.writeByte(canvasData.resolution.ordinal());
            networkBuffer.writeInt(canvasData.width);
            networkBuffer.writeInt(canvasData.height);
            networkBuffer.writeInt(canvasData.getColorDataBuffer().remaining());
            networkBuffer.writeBytes(canvasData.getColorDataBuffer());
            networkBuffer.writeUUID(canvasData.authorUuid);
            networkBuffer.writeUtf(canvasData.authorName, 64);
            networkBuffer.writeUtf(canvasData.title, 32);
        }
    }
}

