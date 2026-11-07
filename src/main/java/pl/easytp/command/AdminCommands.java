package pl.easytp.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.fabricmc.loader.api.FabricLoader;
import pl.easytp.Chat;
import pl.easytp.EasyTP;
import pl.easytp.Msg;
import pl.easytp.backup.BackupManager;
import pl.easytp.clan.ClanTeams;

public final class AdminCommands {
	private AdminCommands() {
	}

	private static BackupManager.Feedback feedback(CommandSourceStack source) {
		return new BackupManager.Feedback() {
			@Override
			public void info(Msg msg, Object... args) {
				source.sendSuccess(() -> Chat.info(msg, args), false);
			}

			@Override
			public void ok(Msg msg, Object... args) {
				source.sendSuccess(() -> Chat.ok(msg, args), true);
			}

			@Override
			public void error(Msg msg, Object... args) {
				source.sendFailure(Chat.error(msg, args));
			}
		};
	}

	private static int withBackups(CommandSourceStack source, boolean list) {
		BackupManager manager = EasyTP.backups();
		if (manager == null) {
			source.sendFailure(Chat.error(Msg.BACKUP_UNAVAILABLE));
			return 0;
		}
		if (list) {
			manager.list(feedback(source));
		} else {
			manager.begin(true, feedback(source));
		}
		return Command.SINGLE_SUCCESS;
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("easytp")
			.executes(context -> {
				String version = FabricLoader.getInstance().getModContainer(EasyTP.MOD_ID)
					.map(mod -> mod.getMetadata().getVersion().getFriendlyString())
					.orElse("?");
				context.getSource().sendSuccess(() -> Component.literal(Msg.INFO.text(version)).withStyle(ChatFormatting.YELLOW), false);
				context.getSource().sendSuccess(() -> Component.literal(Msg.INFO_GAME.text()).withStyle(ChatFormatting.YELLOW), false);
				if (ModerationCommands.isStaff(context.getSource())) {
					context.getSource().sendSuccess(() -> Component.literal(Msg.INFO_MODERATION.text()).withStyle(ChatFormatting.YELLOW), false);
					context.getSource().sendSuccess(() -> Component.literal(Msg.INFO_BACKUP.text()).withStyle(ChatFormatting.YELLOW), false);
				}
				return Command.SINGLE_SUCCESS;
			})
			.then(Commands.literal("backup")
				.requires(ModerationCommands::isStaff)
				.executes(context -> withBackups(context.getSource(), false))
				.then(Commands.literal("list")
					.executes(context -> withBackups(context.getSource(), true))))
			.then(Commands.literal("reload")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.executes(context -> {
					boolean loaded = EasyTP.loadConfig();
					if (loaded) {
						ClanTeams.reconcile(context.getSource().getServer());
						context.getSource().sendSuccess(() -> Component.literal(Msg.RELOADED.text()).withStyle(ChatFormatting.GREEN), true);
					} else {
						context.getSource().sendFailure(Component.literal(Msg.RELOAD_FAILED.text()));
					}
					return loaded ? Command.SINGLE_SUCCESS : 0;
				})));
	}
}
