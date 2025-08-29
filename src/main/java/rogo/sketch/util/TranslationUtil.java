package rogo.sketch.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import rogo.sketch.SketchRender;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class TranslationUtil {
    public static MutableComponent fromLang(String lang) {
        return Component.translatable(SketchRender.MOD_ID + lang);
    }

    public static List<MutableComponent> splitFromLang(String lang) {
        return splitFromLang(lang, Integer.MAX_VALUE);
    }

    public static List<MutableComponent> splitFromLang(String lang, int widthLimit) {
        List<MutableComponent> components = new ArrayList<>();
        MutableComponent mainComponent = Component.translatable(SketchRender.MOD_ID + lang);
        collectComponents(mainComponent, components::add, widthLimit);

        return components;
    }

    public static List<MutableComponent> splitFromComponent(MutableComponent mainComponent, int widthLimit) {
        List<MutableComponent> components = new ArrayList<>();
        collectComponents(mainComponent, components::add, widthLimit);

        return components;
    }

    private static void collectComponents(MutableComponent detailsComponent, Consumer<MutableComponent> consumer, int widthLimit) {
        collectComponent(detailsComponent, consumer, widthLimit);

        for (Component component : detailsComponent.getSiblings()) {
            if (component instanceof MutableComponent mutableComponent) {
                collectComponents(mutableComponent, consumer, widthLimit);
            }
        }
    }

    private static void collectComponent(MutableComponent detailsComponent, Consumer<MutableComponent> consumer, int widthLimit) {
        String mainText = MutableComponent.create(detailsComponent.getContents()).getString();

        String[] parts = mainText.split("\\n");
        for (String part : parts) {
            List<FormattedText> text = Minecraft.getInstance().font.getSplitter().splitLines(Component.literal(part), widthLimit, detailsComponent.getStyle());
            if (text.isEmpty()) {
                consumer.accept(Component.literal(""));
            } else {
                for (FormattedText formattedText : text) {
                    MutableComponent mutableComponent = Component.literal(formattedText.getString());
                    mutableComponent.withStyle(detailsComponent.getStyle());
                    consumer.accept(mutableComponent);
                }
            }
        }
    }
}