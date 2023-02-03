package me.dantaeusb.zetter.server.command;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import me.dantaeusb.zetter.Zetter;
import me.dantaeusb.zetter.core.ZetterItems;
import me.dantaeusb.zetter.item.PaintingItem;
import me.dantaeusb.zetter.storage.AbstractCanvasData;
import me.dantaeusb.zetter.storage.PaintingData;
import net.minecraft.commands.CommandRuntimeException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static java.awt.image.BufferedImage.TYPE_INT_ARGB;

/**
 * Need a lot of magic here
 */
public class ExportCommand {
    private static final DynamicCommandExceptionType ERROR_PAINTING_NOT_FOUND = new DynamicCommandExceptionType((code) -> {
        return Component.translatable("console.zetter.error.painting_not_found", code);
    });

    private static final DynamicCommandExceptionType ERROR_CANNOT_CREATE_FILE = new DynamicCommandExceptionType((code) -> {
        return Component.translatable("console.zetter.error.file_write_error", code);
    });

    static ArgumentBuilder<CommandSourceStack, ?> register() {
        return Commands.literal("export")
            .requires(cs -> cs.hasPermission(Commands.LEVEL_ALL))
            .then(Commands.argument("painting", PaintingLookupArgument.painting()))
            .executes(ctx -> execute(ctx.getSource(), ctx.getSource().getPlayer(), ctx.getSource().getLevel(), PaintingLookupArgument.getPaintingInput(ctx, "painting")));
    }

    private static int execute(CommandSourceStack source, Player player, Level level, PaintingInput paintingInput) throws CommandRuntimeException, CommandSyntaxException {
        if (!paintingInput.hasPaintingData(level)) {
            throw ERROR_PAINTING_NOT_FOUND.create(paintingInput.getPaintingCode());
        }

        File exportDirectory = new File(level.getServer().getServerDirectory(), "zetter");

        if (!exportDirectory.exists()) {
            if (!exportDirectory.mkdir()) {
                throw new CommandRuntimeException(Component.literal("Unable to write painting file, check server logs."));
            }
        } else if (!exportDirectory.isDirectory()) {
            throw new CommandRuntimeException(Component.literal("Unable to write painting file, check server logs."));
        }

        // @todo: Normalize
        File exportFile = new File(exportDirectory, paintingInput.getPaintingData().getPaintingName() + ".png");

        BufferedImage bufferedImage = new BufferedImage(
            AbstractCanvasData.Resolution.x16.getNumeric(),
            AbstractCanvasData.Resolution.x16.getNumeric(),
            TYPE_INT_ARGB
        );

        try {
            ImageIO.write(bufferedImage, "PNG", exportFile);
        } catch (IOException e) {
            // @todo: translatable
            throw new CommandRuntimeException(Component.literal("Unable to write painting file, check server logs."));
        }

        return 1;
    }
}
