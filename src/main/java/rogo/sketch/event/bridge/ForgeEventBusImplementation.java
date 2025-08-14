package rogo.sketch.event.bridge;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ForgeEventBusImplementation implements IEventBusImplementation {
    private final Map<Class<?>, List<EventListener<?>>> listeners = new HashMap<>();

    public ForgeEventBusImplementation() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SuppressWarnings("removal")
    @Override
    public <T> void post(T event) {
        if (event instanceof RegistryEvent registryEvent) {
            FMLJavaModLoadingContext.get().getModEventBus().post(new ProxyModEvent(registryEvent));
        } else {
            MinecraftForge.EVENT_BUS.post(new ProxyEvent<>(event));
        }

        List<EventListener<?>> list = listeners.get(event.getClass());
        if (list != null) {
            for (EventListener<?> l : list) {
                @SuppressWarnings("unchecked")
                EventListener<Object> casted = (EventListener<Object>) l;
                casted.onEvent(event);
            }
        }
    }

    @Override
    public <T> void subscribe(Class<T> eventType, EventListener<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listener);
    }
}