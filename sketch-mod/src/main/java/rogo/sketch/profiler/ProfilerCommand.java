package rogo.sketch.profiler;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class ProfilerCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("profiler")
                .executes(context -> start(context.getSource(), 10.0))
                .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(1.0))
                        .executes(context -> start(context.getSource(),
                                DoubleArgumentType.getDouble(context, "seconds")))));
    }

    private static int start(CommandSourceStack source, double seconds) {
        Profiler.get().startRecording(seconds);
        return 1;
    }
}
