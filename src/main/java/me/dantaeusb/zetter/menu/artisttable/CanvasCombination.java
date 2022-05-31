package me.dantaeusb.zetter.menu.artisttable;

import me.dantaeusb.zetter.block.entity.container.ArtistTableContainer;
import me.dantaeusb.zetter.client.renderer.CanvasRenderer;
import me.dantaeusb.zetter.menu.ArtistTableMenu;
import me.dantaeusb.zetter.core.Helper;
import me.dantaeusb.zetter.core.ZetterItems;
import me.dantaeusb.zetter.item.CanvasItem;
import me.dantaeusb.zetter.storage.AbstractCanvasData;
import me.dantaeusb.zetter.storage.CanvasData;
import me.dantaeusb.zetter.storage.DummyCanvasData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

public class CanvasCombination {
    public static final int[][] paintingShapes = new int[][]{
            {1, 1},
            {1, 2},
            {1, 3},
            {2, 1},
            {2, 2},
            {2, 3},
            {2, 4},
            {3, 1},
            {3, 2},
            {3, 3},
            {3, 4},
            {4, 2},
            {4, 3},
            {4, 4}
    };

    public final State state;
    public final Rectangle rectangle;

    @Nullable
    public final DummyCanvasData canvasData;

    public CanvasCombination(ArtistTableContainer artistTableContainer, Level world) {
        Tuple<Integer, Integer> min = null;
        Tuple<Integer, Integer> max = null;

        for (int y = 0; y < ArtistTableMenu.CANVAS_ROW_COUNT; y++) {
            for (int x = 0; x < ArtistTableMenu.CANVAS_COLUMN_COUNT; x++) {
                if (artistTableContainer.getStackInSlot(y * 4 + x) != ItemStack.EMPTY) {
                    if (min == null) {
                        min = new Tuple<>(x ,y);
                    }

                    if (max == null) {
                        max = new Tuple<>(x ,y);
                        continue;
                    }

                    if (max.getA() < x) {
                        max = new Tuple<>(x, max.getB());
                    } if (max.getB() < y) {
                        max = new Tuple<>(max.getA(), y);
                    }
                }
            }
        }

        if (min == null || max == null) {
            this.state = State.INVALID_SHAPE;
            this.rectangle = CanvasCombination.getZeroRect();
            this.canvasData = null;
            return;
        }

        boolean canvasesReady = true;

        for (int y = 0; y < ArtistTableMenu.CANVAS_ROW_COUNT; y++) {
            for (int x = 0; x < ArtistTableMenu.CANVAS_COLUMN_COUNT; x++) {
                ItemStack currentStack = artistTableContainer.getStackInSlot(y * 4 + x);

                if (currentStack == ItemStack.EMPTY) {
                    if (x >= min.getA() && x <= max.getA()) {
                        if (y >= min.getB() && (y <= max.getB())) {
                            this.state = State.INVALID_SHAPE;
                            this.rectangle = CanvasCombination.getZeroRect();
                            this.canvasData = null;
                            return;
                        }
                    }
                } else if (currentStack.getItem() == ZetterItems.CANVAS.get()) {
                    if ((x < min.getA() || x > max.getA()) || (y < min.getB() || y > max.getB())) {
                        this.state = State.INVALID_SHAPE;
                        this.rectangle = CanvasCombination.getZeroRect();
                        this.canvasData = null;
                        return;
                    }

                    if (CanvasItem.getCanvasData(currentStack, world) == null) {
                        /**
                         * @todo: move request out of here, request with data load attempts but avoid loading unavailable canvases
                         */
                        CanvasRenderer.getInstance().queueCanvasTextureUpdate(
                                AbstractCanvasData.Type.CANVAS,
                                CanvasItem.getCanvasCode(currentStack)
                        );

                        canvasesReady = false;
                    }
                }
            }
        }

        if (!canvasesReady) {
            this.state = State.NOT_LOADED;
            this.rectangle = CanvasCombination.getZeroRect();
            this.canvasData = null;
            return;
        }

        Rectangle rectangle = CanvasCombination.getRect(min, max);

        boolean shapeAvailable = false;
        for (int[] shape: CanvasCombination.paintingShapes) {
            if (rectangle.width == shape[0] && rectangle.height == shape[1]) {
                shapeAvailable = true;
            }
        }

        if (!shapeAvailable) {
            this.state = State.INVALID_SHAPE;
            this.rectangle = CanvasCombination.getZeroRect();
            this.canvasData = null;
            return;
        }

        this.state = State.READY;
        this.rectangle = rectangle;
        this.canvasData = CanvasCombination.createCanvasData(artistTableContainer, rectangle, world);
    }

    public static DummyCanvasData createCanvasData(ArtistTableContainer artistTableContainer, Rectangle rectangle, Level world) {
        final int pixelWidth = rectangle.width * Helper.getResolution().getNumeric();
        final int pixelHeight = rectangle.height * Helper.getResolution().getNumeric();

        ByteBuffer color = ByteBuffer.allocate(pixelWidth * pixelHeight * 4);

        for (int slotY = rectangle.y; slotY < rectangle.y + rectangle.height; slotY++) {
            for (int slotX = rectangle.x; slotX < rectangle.x + rectangle.width; slotX++) {
                ItemStack canvasStack = artistTableContainer.getStackInSlot(slotY * 4 + slotX);

                CanvasData smallCanvasData = CanvasItem.getCanvasData(canvasStack, world);

                int relativeX = slotX - rectangle.x;
                int relativeY = slotY - rectangle.y;


                if (smallCanvasData != null) {
                    for (int smallY = 0; smallY < smallCanvasData.getHeight(); smallY++) {
                        for (int smallX = 0; smallX < smallCanvasData.getWidth(); smallX++) {
                            final int bigX = relativeX * Helper.getResolution().getNumeric() + smallX;
                            final int bigY = relativeY * Helper.getResolution().getNumeric() + smallY;

                            final int colorIndex = (bigY * pixelWidth + bigX) * 4;

                            color.putInt(colorIndex, smallCanvasData.getColorAt(smallX, smallY));
                        }
                    }
                } else {
                    // Use canvas color as background if canvas data is not initialized
                    // @todo: maybe there's a better way to do that
                    for (int smallY = 0; smallY < Helper.getResolution().getNumeric(); smallY++) {
                        for (int smallX = 0; smallX < Helper.getResolution().getNumeric(); smallX++) {
                            final int bigX = relativeX * Helper.getResolution().getNumeric() + smallX;
                            final int bigY = relativeY * Helper.getResolution().getNumeric() + smallY;

                            final int colorIndex = (bigY * pixelWidth + bigX) * 4;

                            color.putInt(colorIndex, Helper.CANVAS_COLOR);
                        }
                    }
                }
            }
        }

        DummyCanvasData combinedCanvasData = DummyCanvasData.createWrap(
            Helper.getResolution(),
            pixelWidth,
            pixelHeight,
            color.array()
        );

        if (world.isClientSide()) {
            Helper.getWorldCanvasTracker(world).registerCanvasData(Helper.COMBINED_CANVAS_CODE, combinedCanvasData);
        }

        return combinedCanvasData;
    }

    public static Rectangle getRect(Tuple<Integer, Integer> min, Tuple<Integer, Integer> max) {
        int width = max.getA() + 1 - min.getA();
        int height = max.getB() + 1 - min.getB();

        return new Rectangle(min.getA(), min.getB(), width, height);
    }

    public static Rectangle getZeroRect() {
        return new Rectangle(0, 0, 0, 0);
    }

    public enum State {
        INVALID_SHAPE,
        NOT_LOADED,
        READY
    }

    private static class Rectangle {
        public final int x;
        public final int y;
        public final int width;
        public final int height;

        Rectangle(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
}