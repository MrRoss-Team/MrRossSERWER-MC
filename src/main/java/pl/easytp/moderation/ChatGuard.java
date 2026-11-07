package pl.easytp.moderation;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import pl.easytp.Chat;
import pl.easytp.EasyTP;
import pl.easytp.Msg;
import pl.easytp.moderation.ModerationStore.Mute;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ChatGuard {
	private ChatGuard() {
	}

	public static void register() {
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> !blocks(sender));
		ServerMessageEvents.ALLOW_COMMAND_MESSAGE.register((message, source, params) -> {
			ServerPlayer player = source.getPlayer();
			return player == null || !blocks(player);
		});
	}

	public static String remaining(Mute mute) {
		return mute.isPermanent()
			? Msg.MOD_PERMANENT.text()
			: Msg.MOD_REMAINING.text(Durations.format(mute.expiresAt() - System.currentTimeMillis()));
	}

	private static boolean blocks(ServerPlayer player) {
		Optional<Mute> mute = EasyTP.moderation().activeMute(player.getUUID());
		if (mute.isEmpty()) {
			return false;
		}
		player.sendSystemMessage(Chat.error(Msg.MUTED_BLOCKED, remaining(mute.get()), mute.get().reason()));
		return true;
	}

	public static void guardPrivateMessages(CommandDispatcher<CommandSourceStack> dispatcher) {
		guard(dispatcher, "msg", "targets", "message");
		guard(dispatcher, "teammsg", "message");
	}

	private static void guard(CommandDispatcher<CommandSourceStack> dispatcher, String literal, String... argumentNames) {
		CommandNode<CommandSourceStack> node = dispatcher.getRoot().getChild(literal);
		List<ArgumentCommandNode<CommandSourceStack, ?>> path = new ArrayList<>();
		for (String name : argumentNames) {
			node = node == null ? null : node.getChild(name);
			if (!(node instanceof ArgumentCommandNode<CommandSourceStack, ?> argument)) {
				EasyTP.LOGGER.warn("EasyTP: komenda /{} ma nieoczekiwaną budowę, wyciszenie nie obejmie jej", literal);
				return;
			}
			path.add(argument);
		}
		Command<CommandSourceStack> original = node.getCommand();
		if (original == null) {
			EasyTP.LOGGER.warn("EasyTP: komenda /{} nie ma wykonawcy, wyciszenie nie obejmie jej", literal);
			return;
		}

		Command<CommandSourceStack> guarded = context -> {
			ServerPlayer player = context.getSource().getPlayer();
			if (player != null) {
				Optional<Mute> mute = EasyTP.moderation().activeMute(player.getUUID());
				if (mute.isPresent()) {
					throw muted(mute.get());
				}
			}
			return original.run(context);
		};

		ArgumentBuilder<CommandSourceStack, ?> chain = builderFor(path.get(path.size() - 1)).executes(guarded);
		for (int i = path.size() - 2; i >= 0; i--) {
			chain = builderFor(path.get(i)).then(chain);
		}
		dispatcher.register(Commands.literal(literal).then(chain));
	}

	public static void ensureNotMuted(ServerPlayer player) throws CommandSyntaxException {
		Optional<Mute> mute = EasyTP.moderation().activeMute(player.getUUID());
		if (mute.isPresent()) {
			throw muted(mute.get());
		}
	}

	private static CommandSyntaxException muted(Mute mute) {
		return Chat.fail(Msg.MUTED_BLOCKED, remaining(mute), mute.reason());
	}

	private static <T> RequiredArgumentBuilder<CommandSourceStack, T> builderFor(ArgumentCommandNode<CommandSourceStack, T> node) {
		return Commands.argument(node.getName(), node.getType());
	}
}
