package rogo.sketch.profiler;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import rogo.sketch.core.pipeline.graph.scheduler.SimpleProfiler;

import java.io.File;

public class ProfilerCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("profiler")
                .executes(context -> start(context.getSource(), 10.0))
                .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(1.0))
                        .executes(context -> start(context.getSource(),
                                DoubleArgumentType.getDouble(context, "seconds")))));
        register_(dispatcher);
    }

    private static int start(CommandSourceStack source, double seconds) {
        Profiler.get().startRecording(seconds);
        return 1;
    }

    public static void register_(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("profiler_")
                .requires(s -> s.hasPermission(2)) // 仅管理员/作弊模式可用
                .executes(context -> start_(context.getSource(), 10.0)) // 默认 10秒
                .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(1.0))
                        .executes(context -> start_(context.getSource(),
                                DoubleArgumentType.getDouble(context, "seconds")))));
    }

    private static int start_(CommandSourceStack source, double seconds) {
        if (SimpleProfiler.get().isRecording()) {
            source.sendFailure(Component.literal("Profiler is already running!"));
            return 0;
        }

        source.sendSystemMessage(Component.literal("Started profiling for " + seconds + " seconds...")
                .withStyle(ChatFormatting.GREEN));

        // 调用工具类，并定义回调函数 (Callback)
        // 当 SimpleProfiler 完成文件写入后，会调用这个 lambda 表达式
        SimpleProfiler.get().startRecording(seconds, (File file) -> {
            // 注意：回调是在 SimpleProfiler 的线程池执行的，
            // 访问 Minecraft 对象最好转回主线程，但发送聊天消息通常是线程安全的。
            sendFeedback(source, file);
        });

        return 1;
    }

    private static void sendFeedback(CommandSourceStack source, File file) {
        // 1. 构建打开文件夹的链接
        Component openFileBtn = Component.literal("[Open Folder]")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.GOLD)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file.getParentFile().getAbsolutePath())));

        // 2. 构建打开 Trace Viewer 网站的链接
        Component openViewerBtn = Component.literal("[Open Viewer]")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.AQUA)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://ui.perfetto.dev/")));

        // 3. 发送组合消息
        source.sendSystemMessage(Component.literal("Profiling finished! Saved to: " + file.getName())
                .withStyle(ChatFormatting.GREEN));

        source.sendSystemMessage(Component.literal("Actions: ").append(openFileBtn).append(" ").append(openViewerBtn));
    }
}
