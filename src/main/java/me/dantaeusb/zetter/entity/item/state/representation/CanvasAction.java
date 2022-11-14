package me.dantaeusb.zetter.entity.item.state.representation;

import me.dantaeusb.zetter.Zetter;
import me.dantaeusb.zetter.painting.Tools;
import me.dantaeusb.zetter.painting.parameters.AbstractToolParameters;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Because actions could be repetitive, we don't send only one action,
 * but try to accumulate similar actions made in short amount of time.
 *
 * To do so, we store common information, like tool, parameters and color
 * In one place, and canvas actions (where action was applied) in another.
 *
 * Theoretically, we also can use relative time of "sub-action" but
 * it's complicated and not yet worth it
 */
public class CanvasAction {
    private static final int MAX_ACTIONS_IN_BUFFER = 100;
    private static final long MAX_TIME = 5000L;
    private static final long MAX_INACTIVE_TIME = 750L;

    public final UUID uuid;
    public final UUID authorId;
    public final Tools tool;

    public final int color;

    public final AbstractToolParameters parameters;
    public final Long startTime;

    /**
     * 1 byte -- meta reserved
     * 2 bytes -- time offset (up to 32s)
     * 4+4 bytes x and y as floats
     * 8 bytes -- extra
     */
    private static final int FRAME_SIZE = 1 + 2 + 8;
    public static final int BUFFER_SIZE = FRAME_SIZE * MAX_ACTIONS_IN_BUFFER;

    private ByteBuffer subActionBuffer;

    private CanvasSubAction lastAction;

    private boolean committed = false;

    // Could be false only on client
    private boolean sent = false;

    // Could be false only on client
    private boolean sync = false;

    private boolean canceled = false;

    public CanvasAction(UUID authorId, Tools tool, int color, AbstractToolParameters parameters) {
        this(authorId, tool, color, parameters, System.currentTimeMillis(), ByteBuffer.allocateDirect(BUFFER_SIZE));
    }

    private CanvasAction(UUID authorId, Tools tool, int color, AbstractToolParameters parameters, Long startTime, ByteBuffer actionBuffer) {
        this.uuid = UUID.randomUUID(); // is it too much?
        this.authorId = authorId;
        this.tool = tool;
        this.color = color;
        this.parameters = parameters;
        this.startTime = startTime;

        this.subActionBuffer = actionBuffer;
    }

    private CanvasAction(UUID actionId, UUID authorId, Tools tool, int color, AbstractToolParameters parameters, Long startTime, ByteBuffer actionBuffer, boolean canceled) {
        this.uuid = actionId;
        this.authorId = authorId;
        this.tool = tool;
        this.color = color;
        this.parameters = parameters;
        this.startTime = startTime;

        // When we create with data, it should be non-editable
        this.subActionBuffer = actionBuffer.asReadOnlyBuffer();

        this.committed = true;
        this.sent = true;
        this.sync = true;
        this.canceled = canceled;
    }

    /**
     * Can we just add frame here
     *
     * @param authorId
     * @param tool
     * @param parameters
     * @return
     */
    public boolean canContinue(UUID authorId, Tools tool, int color, AbstractToolParameters parameters) {
        // Not committed, should not yet be committed, and action is compatible
        return !this.committed && !this.shouldCommit() && this.isActionCompatible(authorId, tool, color, parameters);
    }

    /**
     * Is action we're trying to extend compatible with current action
     * @param authorId
     * @param tool
     * @param parameters
     * @return
     */
    public boolean isActionCompatible(UUID authorId, Tools tool, int color, AbstractToolParameters parameters) {
        return this.authorId == authorId && this.tool == tool && this.color == color;
    }

    /**
     * Should we stop writing in this action and start new
     * @return
     */
    public boolean shouldCommit() {
        final long currentTime = System.currentTimeMillis();

        // Action started long ago
        if (currentTime - this.startTime > MAX_TIME) {
            return true;
        }

        // Did nothing for a while
        if (this.lastAction != null && (currentTime - (this.startTime + this.lastAction.time)) > MAX_INACTIVE_TIME) {
            return true;
        }

        if (!this.subActionBuffer.hasRemaining()) {
            return true;
        }

        return false;
    }

    /**
     * Add action coordinates (like path of the brush)
     * Returns true if can add, false if it should be committed
     * and new action stack prepared
     *
     * @param posX
     * @param posY
     */
    public void addFrame(float posX, float posY) {
        if (this.committed) {
            throw new IllegalStateException("Cannot add frame to committed action buffer");
        }

        if (this.shouldCommit()) {
            throw new IllegalStateException("Cannot add frame to action buffer that should be committed");
        }

        final long currentTime = System.currentTimeMillis();
        final int passedTime = (int) (currentTime - this.startTime);

        final CanvasSubAction action = new CanvasSubAction(passedTime, posX, posY);
        CanvasSubAction.writeToBuffer(action, this.subActionBuffer);

        this.lastAction = action;
    }

    public Stream<CanvasSubAction> getSubActionStream() {
        ByteBuffer subActionBuffer;

        if (this.isCommitted()) {
            subActionBuffer = this.subActionBuffer;
        } else {
            subActionBuffer = this.subActionBuffer.duplicate().flip();
        }

        subActionBuffer.rewind();

        if (subActionBuffer.limit() % FRAME_SIZE != 0) {
            throw new IllegalStateException("Incorrect amount of frames in buffer");
        } else if (subActionBuffer.limit() == 0) {
            throw new IllegalStateException("Applied action buffer is empty");
        }

        return Stream.generate(() -> CanvasSubAction.readFromBuffer(subActionBuffer)).limit(subActionBuffer.limit() / FRAME_SIZE);
    }

    public int countActions() {
        if (this.subActionBuffer.isReadOnly()) {
            return this.subActionBuffer.limit() / FRAME_SIZE;
        }

        return this.subActionBuffer.position() / FRAME_SIZE;
    }

    public void commit() {
        if (this.committed) {
            Zetter.LOG.warn("Already committed");
            return;
        }

        if (this.subActionBuffer.limit() == 0) {
            Zetter.LOG.warn("Committing empty action buffer!");
        }

        this.sealBuffer();
        this.committed = true;
    }

    public boolean isCommitted() {
        return this.committed;
    }

    public void setSent() {
        if (!this.committed) {
            this.commit();
        }

        this.sent = true;
    }

    public boolean isSent() {
        return this.sent;
    }

    public void setSync() {
        this.sync = true;
    }

    public boolean isSync() {
        return this.sync;
    }

    public void undo() {
        if (!this.committed) {
            this.commit();
        }

        this.canceled = true;
    }

    public void redo() {
        if (!this.committed) {
            this.commit();
        }

        this.canceled = false;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }

    public boolean isCanceled() {
        return this.canceled;
    }

    public @Nullable CanvasSubAction getLastAction() {
        return this.lastAction;
    }

    /**
     * Prepare the data for sending - flip & lock
     * @return
     */
    public void sealBuffer() {
        this.subActionBuffer.flip();
        this.subActionBuffer = this.subActionBuffer.asReadOnlyBuffer();
    }

    public static class CanvasSubAction {
        public final byte meta;

        public final int time;
        public final float posX;
        public final float posY;

        public CanvasSubAction(int time, float posX, float posY) {
            this.meta = (byte) 0x1;

            if (time > 0xFFFF) {
                throw new IllegalStateException("Time offset for action is to big");
            }

            this.time = time;
            this.posX = posX;
            this.posY = posY;
        }

        private CanvasSubAction(byte meta, int time, float posX, float posY) {
            this.meta = meta;
            this.time = time;
            this.posX = posX;
            this.posY = posY;
        }

        public static void writeToBuffer(CanvasSubAction action, ByteBuffer buffer) {
            // Meta
            buffer.put((byte) 0x1);

            // Time
            buffer.put((byte) (action.time & 0xFF));
            buffer.put((byte) ((action.time >> 8) & 0xFF));

            // Position
            buffer.putFloat(action.posX);
            buffer.putFloat(action.posY);
        }

        public static CanvasSubAction readFromBuffer(ByteBuffer buffer) {
            // Meta
            final byte meta = buffer.get();

            // Time
            final int time = buffer.get() << 8 & buffer.get();

            // Position
            final float posX = buffer.getFloat();
            final float posY = buffer.getFloat();

            return new CanvasSubAction(meta, time, posX, posY);
        }
    }

    public static void writePacketData(CanvasAction actionBuffer, FriendlyByteBuf buffer) {
        buffer.writeUUID(actionBuffer.uuid);
        buffer.writeUUID(actionBuffer.authorId);
        buffer.writeUtf(actionBuffer.tool.toString(), 32);
        buffer.writeInt(actionBuffer.color);
        buffer.writeLong(actionBuffer.startTime);
        buffer.writeBoolean(actionBuffer.canceled);
        AbstractToolParameters.writePacketData(actionBuffer.parameters, buffer);

        buffer.writeInt(actionBuffer.subActionBuffer.rewind().limit());
        buffer.writeBytes(actionBuffer.subActionBuffer);
    }

    public static CanvasAction readPacketData(FriendlyByteBuf buffer) {
        UUID actionId = buffer.readUUID();
        UUID authorId = buffer.readUUID();
        Tools tool = Tools.valueOf(buffer.readUtf(32));
        int color = buffer.readInt();
        Long startTime = buffer.readLong();
        boolean canceled = buffer.readBoolean();
        AbstractToolParameters parameters = AbstractToolParameters.readPacketData(buffer, tool);

        int bufferSize = buffer.readInt();
        try {
            ByteBuffer actionsBuffer = buffer.readBytes(bufferSize).nioBuffer();

            return new CanvasAction(
                    actionId,
                    authorId,
                    tool,
                    color,
                    parameters,
                    startTime,
                    actionsBuffer,
                    canceled
            );
        } catch (IndexOutOfBoundsException e) {
            Zetter.LOG.error(e);
        }

        return null;
    }
}
