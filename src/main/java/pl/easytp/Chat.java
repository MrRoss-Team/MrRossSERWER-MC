package pl.easytp;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

public final class Chat {
	private Chat() {
	}

	public static MutableComponent ok(Msg msg, Object... args) {
		return Component.literal(msg.text(args)).withStyle(ChatFormatting.GREEN);
	}

	public static MutableComponent info(Msg msg, Object... args) {
		return Component.literal(msg.text(args)).withStyle(ChatFormatting.YELLOW);
	}

	public static MutableComponent error(Msg msg, Object... args) {
		return Component.literal(msg.text(args)).withStyle(ChatFormatting.RED);
	}

	public static CommandSyntaxException fail(Msg msg, Object... args) {
		return new SimpleCommandExceptionType(Component.literal(msg.text(args))).create();
	}

	public static MutableComponent button(String label, ChatFormatting color, boolean bold, String command, String hover) {
		return Component.literal(label).withStyle(style -> style
			.withColor(color)
			.withBold(bold)
			.withClickEvent(new ClickEvent.RunCommand(command))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
	}
}
