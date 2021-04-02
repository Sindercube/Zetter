package com.dantaeusb.zetter.client.gui.painting;

import com.dantaeusb.zetter.Zetter;
import com.dantaeusb.zetter.client.gui.PaintingScreen;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.IGuiEventListener;
import net.minecraft.client.gui.IRenderable;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.renderer.texture.ITickable;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;

public class ColorCodeWidget extends AbstractPaintingWidget implements IRenderable, IGuiEventListener {
    final static int TEXTBOX_WIDTH = 82;
    final static int TEXTBOX_HEIGHT = 16;

    final static int MODE_BUTTON_WIDTH = 17;
    final static int MODE_BUTTON_HEIGHT = 16;

    TextFieldWidget textField;

    private Mode textAreaMode;

    public ColorCodeWidget(PaintingScreen parentScreen, int x, int y) {
        super(parentScreen, x, y, TEXTBOX_WIDTH + MODE_BUTTON_WIDTH * 2, TEXTBOX_HEIGHT, new TranslationTextComponent("container.zetter.painting.tools"));
    }

    public void initFields() {
        this.textField = new TextFieldWidget(
                this.parentScreen.getFont(),
                this.x + 4,
                this.y + 4,
                TEXTBOX_WIDTH - 7,
                12,
                new TranslationTextComponent("container.zetter.easel")
        );

        this.textField.setCanLoseFocus(false);
        this.textField.setTextColor(-1);
        this.textField.setDisabledTextColour(-1);
        this.textField.setEnableBackgroundDrawing(false);
        this.textField.setMaxStringLength(32);
        //this.textField.setResponder(this::renameItem);
        this.parentScreen.addChildren(this.textField);
    }

    public void tick() {
        this.textField.tick();
    }

    /**
     * Cancel closing screen when pressing "E", handle input properly
     * @param keyCode
     * @param scanCode
     * @param modifiers
     * @return
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.textField.isFocused()) {
            return this.textField.keyPressed(keyCode, scanCode, modifiers) || this.textField.canWrite() || super.keyPressed(keyCode, scanCode, modifiers);
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int iMouseX = (int) mouseX;
        int iMouseY = (int) mouseY;

        // Quick check
        if (PaintingScreen.isInRect(this.x, this.y, TEXTBOX_WIDTH, TEXTBOX_HEIGHT, iMouseX, iMouseY)) {
            this.textField.setFocused2(true);
            return true;
        }

        this.textField.setFocused2(false);
        return false;
    }

    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        drawTextbox(matrixStack);
        drawModeButtons(matrixStack);

        this.textField.render(matrixStack, mouseX, mouseY, partialTicks);
    }

    protected void drawTextbox(MatrixStack matrixStack) {
        final int TEXTBOX_POSITION_U = 0;
        final int TEXTBOX_POSITION_V = 185;

        int textboxV = TEXTBOX_POSITION_V + (this.textField.isFocused() ? TEXTBOX_HEIGHT : 0);

        this.blit(matrixStack, this.x, this.y, TEXTBOX_POSITION_U, textboxV, TEXTBOX_WIDTH, TEXTBOX_HEIGHT);
    }

    protected void drawModeButtons(MatrixStack matrixStack) {
        final int MODE_BUTTON_POSITION_U = 176;
        final int MODE_BUTTON_POSITION_V = 64;

        int modeNameV = MODE_BUTTON_POSITION_V + (this.textAreaMode == Mode.NAME ? MODE_BUTTON_WIDTH : 0);
        int modeColorV = MODE_BUTTON_POSITION_V + (this.textAreaMode == Mode.COLOR ? MODE_BUTTON_HEIGHT : 0);

        this.blit(matrixStack, this.x - MODE_BUTTON_WIDTH - 1, this.y, MODE_BUTTON_POSITION_U, modeNameV, MODE_BUTTON_WIDTH, MODE_BUTTON_HEIGHT);
        this.blit(matrixStack, this.x + TEXTBOX_WIDTH + 1, this.y, MODE_BUTTON_POSITION_U + MODE_BUTTON_WIDTH, modeColorV, MODE_BUTTON_WIDTH, MODE_BUTTON_HEIGHT);
    }

    public enum Mode {
        NAME,
        COLOR
    }
}