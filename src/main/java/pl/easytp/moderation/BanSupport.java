package pl.easytp.moderation;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public final class BanSupport {
	private static final String DATE_PATTERN = "yyyy-MM-dd 'at' HH:mm:ss z";
	private static final NameAndId PURGE_KEY = new NameAndId(new UUID(0L, 0L), "easytp-purge");

	private BanSupport() {
	}

	public static void purgeExpired(MinecraftServer server) {
		server.getPlayerList().getBans().get(PURGE_KEY);
	}

	public static Component kickMessage(String reason, Date expires) {
		MutableComponent message = Component.translatable("multiplayer.disconnect.banned.reason", Component.literal(reason));
		if (expires != null) {
			message.append(Component.translatable("multiplayer.disconnect.banned.expiration",
				new SimpleDateFormat(DATE_PATTERN, Locale.ROOT).format(expires)));
		}
		return message;
	}
}
