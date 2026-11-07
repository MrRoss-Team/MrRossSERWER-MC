package pl.easytp;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.easytp.boxpvp.BoxPvp;
import pl.easytp.clan.ClanStore;
import pl.easytp.clan.ClanTeams;
import pl.easytp.command.AdminCommands;
import pl.easytp.command.BoxCommands;
import pl.easytp.command.ClanCommands;
import pl.easytp.command.DuelCommands;
import pl.easytp.command.GameCommands;
import pl.easytp.command.HomeCommands;
import pl.easytp.command.IslandCommands;
import pl.easytp.command.ModerationCommands;
import pl.easytp.command.SpawnCommands;
import pl.easytp.command.TpaCommands;
import pl.easytp.backup.BackupManager;
import pl.easytp.config.EasyTPConfig;
import pl.easytp.data.DataStore;
import pl.easytp.duel.DuelManager;
import pl.easytp.economy.MarketStore;
import pl.easytp.lifesteal.KillRules;
import pl.easytp.lifesteal.LifeSteal;
import pl.easytp.moderation.BanSupport;
import pl.easytp.moderation.ChatGuard;
import pl.easytp.moderation.ModerationStore;
import pl.easytp.oneblock.IslandStore;
import pl.easytp.oneblock.OneBlock;
import pl.easytp.player.PlayerStore;
import pl.easytp.session.Realms;
import pl.easytp.session.StateFiles;
import pl.easytp.stats.PlayerStats;
import pl.easytp.teleport.TeleportService;
import pl.easytp.tpa.TpaManager;
import pl.easytp.util.Cooldowns;

import java.io.IOException;
import java.nio.file.Path;

public class EasyTP implements ModInitializer {
	public static final String MOD_ID = "easytp";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private record Services(DataStore store, ModerationStore moderation, PlayerStore players, MarketStore market, KillRules killRules,
		ClanStore clans, DuelManager duels, IslandStore islands, Realms realms, BoxPvp box, TpaManager tpa, TeleportService teleports, Cooldowns requestGuard) {
	}

	private static final long KILL_MEMORY_MILLIS = 7L * 24 * 60 * 60 * 1000;

	private static volatile EasyTPConfig config = new EasyTPConfig();
	private static volatile Services services;
	private static volatile BackupManager backups;

	@Override
	public void onInitialize() {
		loadConfig();

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			HomeCommands.register(dispatcher);
			TpaCommands.register(dispatcher);
			SpawnCommands.register(dispatcher);
			ModerationCommands.register(dispatcher);
			AdminCommands.register(dispatcher);
			GameCommands.register(dispatcher);
			ClanCommands.register(dispatcher);
			DuelCommands.register(dispatcher);
			IslandCommands.register(dispatcher);
			BoxCommands.register(dispatcher);
			ChatGuard.guardPrivateMessages(dispatcher);
		});
		ChatGuard.register();
		LifeSteal.register();
		DuelManager.registerEvents();
		Realms.registerEvents();
		OneBlock.registerEvents();
		BoxPvp.registerEvents();

		ServerLifecycleEvents.SERVER_STARTING.register(EasyTP::onServerStarting);
		ServerLifecycleEvents.SERVER_STARTED.register(EasyTP::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(EasyTP::onServerStopping);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			services = null;
			backups = null;
		});
		ServerTickEvents.END_SERVER_TICK.register(EasyTP::onTick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ClanTeams.onJoin(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onDisconnect(handler.getPlayer()));
		ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> BanSupport.purgeExpired(server));

		LOGGER.info("EasyTP gotowy: /sethome /home /delhome /homes /tpa /tpahere /tpaccept /tpdeny /tpcancel /spawn"
			+ " oraz moderacja: /tempban /unban /mute /tempmute /unmute /mutes /warn /warnings /clearwarnings /history"
			+ ", kopie zapasowe świata: /easytp backup"
			+ " oraz tryb gry: /serca /wyplacserce /sklepzaserca /stan /pay /baltop /eco /sprzedaj /sklep /rynek /topki /statystyki /klan /duel /ranked /elo /kity /is /motyw /box /kit /ulepsz /warpy");
	}

	public static EasyTPConfig config() {
		return config;
	}

	public static DataStore store() {
		return services().store();
	}

	public static ModerationStore moderation() {
		return services().moderation();
	}

	public static PlayerStore players() {
		return services().players();
	}

	public static MarketStore market() {
		return services().market();
	}

	public static KillRules killRules() {
		return services().killRules();
	}

	public static ClanStore clans() {
		return services().clans();
	}

	public static DuelManager duels() {
		return services().duels();
	}

	public static DuelManager duelsOrNull() {
		Services current = services;
		return current == null ? null : current.duels();
	}

	public static IslandStore islands() {
		return services().islands();
	}

	public static IslandStore islandsOrNull() {
		Services current = services;
		return current == null ? null : current.islands();
	}

	public static Realms realmsOrNull() {
		Services current = services;
		return current == null ? null : current.realms();
	}

	public static BoxPvp box() {
		return services().box();
	}

	public static BoxPvp boxOrNull() {
		Services current = services;
		return current == null ? null : current.box();
	}

	public static TpaManager tpa() {
		return services().tpa();
	}

	public static TeleportService teleports() {
		return services().teleports();
	}

	public static Cooldowns requestGuard() {
		return services().requestGuard();
	}

	public static BackupManager backups() {
		return backups;
	}

	private static Services services() {
		Services current = services;
		if (current == null) {
			throw new IllegalStateException("EasyTP: serwer nie jest uruchomiony");
		}
		return current;
	}

	public static boolean loadConfig() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("easytp.json");
		try {
			EasyTPConfig loaded = EasyTPConfig.load(file);
			config = loaded;
			Msg.setLanguage(loaded.language);
			return true;
		} catch (IOException | RuntimeException e) {
			LOGGER.error("EasyTP: nie udało się wczytać konfiguracji {}", file, e);
			return false;
		}
	}

	private static void onServerStarting(MinecraftServer server) {
		loadConfig();

		Path dataDir = server.getWorldPath(LevelResource.ROOT).resolve("easytp");
		DataStore store = new DataStore(dataDir.resolve("data.json"));
		try {
			store.load().ifPresent(backup -> LOGGER.error("EasyTP: uszkodzony plik danych odłożono jako {}", backup));
		} catch (IOException e) {
			LOGGER.error("EasyTP: nie udało się wczytać danych z {}", dataDir.resolve("data.json"), e);
		}

		ModerationStore moderation = new ModerationStore(dataDir.resolve("moderation.json"), System::currentTimeMillis);
		try {
			moderation.load().ifPresent(backup -> LOGGER.error("EasyTP: uszkodzony plik moderacji odłożono jako {}", backup));
		} catch (IOException e) {
			LOGGER.error("EasyTP: nie udało się wczytać moderacji z {}", dataDir.resolve("moderation.json"), e);
		}

		PlayerStore players = new PlayerStore(dataDir.resolve("players.json"));
		try {
			players.load().ifPresent(backup -> LOGGER.error("EasyTP: uszkodzony plik graczy odłożono jako {}", backup));
		} catch (IOException e) {
			LOGGER.error("EasyTP: nie udało się wczytać graczy z {}", dataDir.resolve("players.json"), e);
		}

		MarketStore market = new MarketStore(dataDir.resolve("market.json"));
		try {
			market.load().ifPresent(backup -> LOGGER.error("EasyTP: uszkodzony plik rynku odłożono jako {}", backup));
		} catch (IOException e) {
			LOGGER.error("EasyTP: nie udało się wczytać rynku z {}", dataDir.resolve("market.json"), e);
		}

		ClanStore clans = new ClanStore(dataDir.resolve("clans.json"), System::currentTimeMillis);
		try {
			clans.load().ifPresent(backup -> LOGGER.error("EasyTP: uszkodzony plik klanów odłożono jako {}", backup));
		} catch (IOException e) {
			LOGGER.error("EasyTP: nie udało się wczytać klanów z {}", dataDir.resolve("clans.json"), e);
		}

		IslandStore islands = new IslandStore(dataDir.resolve("islands.json"), System::currentTimeMillis);
		try {
			islands.load().ifPresent(backup -> LOGGER.error("EasyTP: uszkodzony plik wysp odłożono jako {}", backup));
		} catch (IOException e) {
			LOGGER.error("EasyTP: nie udało się wczytać wysp z {}", dataDir.resolve("islands.json"), e);
		}

		services = new Services(
			store,
			moderation,
			players,
			market,
			new KillRules(System::currentTimeMillis),
			clans,
			new DuelManager(new StateFiles(dataDir.resolve("duel-snapshots"))),
			islands,
			new Realms(dataDir.resolve("profiles")),
			new BoxPvp(),
			new TpaManager(System::currentTimeMillis),
			new TeleportService(EasyTP::config),
			new Cooldowns(System::currentTimeMillis));
	}

	private static void onServerStarted(MinecraftServer server) {
		ClanTeams.reconcile(server);
		try {
			backups = BackupManager.start(server);
		} catch (RuntimeException e) {
			LOGGER.error("EasyTP: nie udało się uruchomić kopii zapasowych", e);
		}
	}

	private static void onServerStopping(MinecraftServer server) {
		BackupManager manager = backups;
		if (manager != null) {
			manager.stop();
		}
		Services current = services;
		if (current == null) {
			return;
		}
		try {
			current.store().save();
			current.moderation().save();
			PlayerStats.syncAll(server);
			current.players().save();
			current.market().save();
			current.clans().save();
			current.islands().save();
		} catch (IOException e) {
			LOGGER.error("EasyTP: nie udało się zapisać danych przy zamykaniu serwera", e);
		}
	}

	private static void onTick(MinecraftServer server) {
		Services current = services;
		if (current == null) {
			return;
		}
		try {
			current.teleports().tick(server);
			current.duels().tick(server);
			OneBlock.tick(server);
			current.box().tick(server);

			if (server.getTickCount() % 20 == 0) {
				for (TpaManager.Request expired : current.tpa().removeExpired()) {
					ServerPlayer sender = server.getPlayerList().getPlayer(expired.sender());
					ServerPlayer target = server.getPlayerList().getPlayer(expired.target());
					if (sender != null && target != null) {
						sender.sendSystemMessage(Chat.info(Msg.TPA_EXPIRED, target.getPlainTextName()));
					}
				}

				BanSupport.purgeExpired(server);
				for (ModerationStore.Mute mute : current.moderation().purgeExpiredMutes()) {
					ServerPlayer player = server.getPlayerList().getPlayer(mute.id());
					if (player != null) {
						player.sendSystemMessage(Chat.ok(Msg.UNMUTE_EXPIRED));
					}
				}
			}

			if (server.getTickCount() % 1200 == 0) {
				PlayerStats.syncAll(server);
				current.players().flush();
				current.killRules().prune(KILL_MEMORY_MILLIS);
			}
		} catch (RuntimeException e) {
			LOGGER.error("EasyTP: błąd w pętli serwera", e);
		}
	}

	private static void onDisconnect(ServerPlayer player) {
		Services current = services;
		if (current == null) {
			return;
		}
		current.teleports().forget(player.getUUID());
		current.tpa().removeInvolving(player.getUUID());
		current.duels().onDisconnect(player);
		current.box().onDisconnect(player);
		PlayerStats.sync(player);
		current.players().flush();
	}
}
