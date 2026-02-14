package rogo.sketch.profiler;

import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import rogo.sketch.SketchRender;

public class ProfilerEventHandler {

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ProfilerCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onPlayerLeave(ClientPlayerNetworkEvent.LoggingOut event) {
        ProfilerWebServer.stop();
        Profiler.get().stopRecording(); // Ensure recording stops if player leaves
    }
}
