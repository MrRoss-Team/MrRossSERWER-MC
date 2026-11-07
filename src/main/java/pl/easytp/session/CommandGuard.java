package pl.easytp.session;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import pl.easytp.Chat;
import pl.easytp.EasyTP;
import pl.easytp.Msg;
import pl.easytp.boxpvp.BoxPvp;
import pl.easytp.duel.DuelManager;

import java.util.Locale;
import java.util.Set;

public final class CommandGuard {
	private static final Set<String> DUEL_ALLOWED = Set.of("surrender", "ff", "poddaj", "msg", "tell", "w", "k");
	private static final Set<String> MAIN_ONLY = Set.of("wyplacserce", "sklepzaserca");
	private static final Set<String> COMBAT_ALLOWED = Set.of("msg", "tell", "w", "k");
	private static final Set<String> BOX_BLOCKED = Set.of("sklep", "rynek", "sprzedaj");

	private CommandGuard() {
	}

	public static boolean blocks(CommandSourceStack source, String command) {
		ServerPlayer player = source.getPlayer();
		if (player == null || source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
			return false;
		}
		DuelManager duels = EasyTP.duelsOrNull();
		if (duels == null) {
			return false;
		}
		String root = rootOf(command);
		if (duels.inDuel(player.getUUID())) {
			if (DUEL_ALLOWED.contains(root)) {
				return false;
			}
			player.sendSystemMessage(Chat.error(Msg.DUEL_COMMAND_BLOCKED));
			return true;
		}
		BoxPvp box = EasyTP.boxOrNull();
		if (box != null && box.isTagged(player.getUUID()) && !COMBAT_ALLOWED.contains(root)) {
			player.sendSystemMessage(Chat.error(Msg.BOX_TAGGED, box.tagSeconds(player.getUUID())));
			return true;
		}
		Realms.Realm realm = Realms.of(player);
		if ((realm != Realms.Realm.MAIN && MAIN_ONLY.contains(root)) || (realm == Realms.Realm.BOXPVP && BOX_BLOCKED.contains(root))) {
			player.sendSystemMessage(Chat.error(Msg.REALM_BLOCKED));
			return true;
		}
		return false;
	}

	static String rootOf(String command) {
		String text = command.startsWith("/") ? command.substring(1) : command;
		int space = text.indexOf(' ');
		String root = space < 0 ? text : text.substring(0, space);
		int colon = root.indexOf(':');
		return (colon < 0 ? root : root.substring(colon + 1)).toLowerCase(Locale.ROOT);
	}
}
