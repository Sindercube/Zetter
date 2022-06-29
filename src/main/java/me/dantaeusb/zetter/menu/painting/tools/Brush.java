package me.dantaeusb.zetter.menu.painting.tools;

import me.dantaeusb.zetter.menu.EaselContainerMenu;
import me.dantaeusb.zetter.menu.painting.parameters.AbstractToolParameter;
import me.dantaeusb.zetter.menu.painting.pipes.BrushPipe;
import me.dantaeusb.zetter.menu.painting.pipes.DitheringPipe;
import me.dantaeusb.zetter.menu.painting.pipes.Pipe;
import me.dantaeusb.zetter.storage.CanvasData;
import net.minecraft.network.chat.TranslatableComponent;

import java.util.HashMap;
import java.util.LinkedList;

public class Brush extends AbstractTool {
    public static final String CODE = "brush";

    private final TranslatableComponent translatableComponent = new TranslatableComponent("container.zetter.painting.tools.brush");

    public Brush(EaselContainerMenu menu) {
        super(Brush.CODE, menu, new LinkedList<Pipe>() {{
            new DitheringPipe();
            new BrushPipe();
        }});
    }

    @Override
    public ToolShape getShape(HashMap<String, AbstractToolParameter> params) {
        return null;
    }

    @Override
    public TranslatableComponent getTranslatableComponent() {
        return this.translatableComponent;
    }

    @Override
    public int apply(CanvasData canvas, HashMap<String, AbstractToolParameter> params, int color, float posX, float posY) {
        return 1;
    }
}
