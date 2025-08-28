package rogo.sketch.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class TranslationUtil {
    public static MutableComponent fromLang(String lang) {
        return MutableComponent.create(Component.literal(lang).getContents());
    }
}