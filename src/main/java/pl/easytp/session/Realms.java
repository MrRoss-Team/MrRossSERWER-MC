package pl.easytp.session;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import pl.easytp.EasyTP;
import pl.easytp.boxpvp.BoxPvp;
import pl.easytp.duel.DuelManager;
import pl.easytp.lifesteal.LifeSteal;
import pl.easytp.oneblock.OneBlock;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class Realms {
	public enum Realm {
		MAIN,
		ONEBLOCK,
		BOXPVP,
		ARENA
	}

	public static final ResourceKey<Level> ONEBLOCK_LEVEL = key("oneblock");
	public static final ResourceKey<Level> BOXPVP_LEVEL = key("boxpvp");

	private final Map<Realm, StateFiles> profiles = new EnumMap<>(Realm.class);
	private final Set<UUID> keepProfile = new HashSet<>();

	public Realms(Path dir) {
		for (Realm realm : Realm.values()) {
			if (realm != Realm.ARENA) {
				profiles.put(realm, new StateFiles(dir.resolve(realm.name().toLowerCase(java.util.Locale.ROOT))));
			}
		}
	}

	private static ResourceKey<Level> key(String path) {
		return ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(EasyTP.MOD_ID, path));
	}

	public static Realm of(ResourceKey<Level> dimension) {
		if (dimension.equals(ONEBLOCK_LEVEL)) {
			return Realm.ONEBLOCK;
		}
		if (dimension.equals(BOXPVP_LEVEL)) {
			return Realm.BOXPVP;
		}
		if (dimension.equals(DuelManager.DIMENSION)) {
			return Realm.ARENA;
		}
		return Realm.MAIN;
	}

	public static Realm of(ServerPlayer player) {
		return of(player.level().dimension());
	}

	public static void registerEvents() {
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
			Realms realms = EasyTP.realmsOrNull();
			if (realms != null) {
				realms.onChangeWorld(player, of(origin.dimension()), of(destination.dimension()));
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			Realms realms = EasyTP.realmsOrNull();
			if (realms != null && !alive) {
				realms.onRespawn(newPlayer, of(oldPlayer));
			}
		});
	}

	private void onChangeWorld(ServerPlayer player, Realm from, Realm to) {
		if (from == to || from == Realm.ARENA || to == Realm.ARENA || keepProfile.contains(player.getUUID())) {
			return;
		}
		switchProfile(player, from, to);
	}

	private void switchProfile(ServerPlayer player, Realm from, Realm to) {
		try {
			profiles.get(from).save(player.getUUID(), PlayerState.capture(player));
		} catch (IOException | RuntimeException e) {
			EasyTP.LOGGER.error("EasyTP: nie udało się zapisać ekwipunku gracza {} w trybie {}", player.getPlainTextName(), from, e);
		}
		Optional<JsonObject> state = profiles.get(to).load(player.getUUID());
		if (state.isPresent()) {
			PlayerState.restore(player, state.get(), false);
			LifeSteal.applyHealth(player, false);
			PlayerState.restoreHealth(player, state.get());
		} else {
			LifeSteal.applyHealth(player, false);
			PlayerState.clear(player);
		}
	}

	private void onRespawn(ServerPlayer player, Realm died) {
		Realm current = of(player);
		switch (died) {
			case MAIN -> {
				if (current != Realm.MAIN) {
					keepProfile(player, () -> PlayerState.teleportToWorldSpawn(player));
				}
			}
			case ONEBLOCK -> {
				Optional<OneBlock.Spot> spot = OneBlock.homeOf(player);
				if (spot.isPresent()) {
					keepProfile(player, () -> PlayerState.teleport(player, spot.get().level(), spot.get().pos(), 0, 0));
				} else if (current == Realm.MAIN) {
					switchProfile(player, Realm.ONEBLOCK, Realm.MAIN);
				}
			}
			case BOXPVP -> {
				BoxPvp box = EasyTP.boxOrNull();
				if (box != null) {
					box.spawn(player.level().getServer()).ifPresent(spot ->
						keepProfile(player, () -> PlayerState.teleport(player, spot.level(), spot.pos(), 0, 0)));
					box.onRespawn(player);
				}
			}
			default -> {
			}
		}
		LifeSteal.applyHealth(player, true);
	}

	private void keepProfile(ServerPlayer player, Runnable move) {
		keepProfile.add(player.getUUID());
		try {
			move.run();
		} finally {
			keepProfile.remove(player.getUUID());
		}
	}
}
