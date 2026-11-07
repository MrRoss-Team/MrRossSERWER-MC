package pl.easytp.duel;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import pl.easytp.Chat;
import pl.easytp.EasyTP;
import pl.easytp.Msg;
import pl.easytp.boxpvp.BoxPvp;
import pl.easytp.config.EasyTPConfig;
import pl.easytp.lifesteal.LifeSteal;
import pl.easytp.player.PlayerData;
import pl.easytp.player.PlayerStore;
import pl.easytp.session.PlayerState;
import pl.easytp.session.StateFiles;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class DuelManager {
	public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(EasyTP.MOD_ID, "duels"));
	private static final int ROUND_END_TICKS = 40;
	private static final int EFFECT_TICKS = 20 * 60 * 30;
	private static final double FALL_MARGIN = 3;

	private enum Phase {
		COUNTDOWN,
		FIGHT,
		ROUND_END
	}

	private static final class Duel {
		final UUID[] ids;
		final String[] names;
		final int[] wins = new int[2];
		final Kits.Kit kit;
		final int slot;
		final boolean ranked;
		final int winsNeeded;
		Phase phase = Phase.COUNTDOWN;
		int phaseTicks;
		int fightTicks;
		int pendingWinner = -1;

		Duel(ServerPlayer a, ServerPlayer b, Kits.Kit kit, int slot, boolean ranked, int bestOf) {
			this.ids = new UUID[]{a.getUUID(), b.getUUID()};
			this.names = new String[]{a.getPlainTextName(), b.getPlainTextName()};
			this.kit = kit;
			this.slot = slot;
			this.ranked = ranked;
			this.winsNeeded = bestOf / 2 + 1;
		}

		int sideOf(UUID id) {
			return ids[0].equals(id) ? 0 : 1;
		}
	}

	private final StateFiles snapshots;
	private final Lobby lobby = new Lobby(System::currentTimeMillis);
	private final Arenas arenas = new Arenas();
	private final Map<UUID, Duel> byPlayer = new HashMap<>();
	private final List<Duel> duels = new ArrayList<>();

	public DuelManager(StateFiles snapshots) {
		this.snapshots = snapshots;
	}

	public static void registerEvents() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			DuelManager manager = EasyTP.duelsOrNull();
			return manager == null || !(entity instanceof ServerPlayer player) || manager.allowDamage(player);
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			DuelManager manager = EasyTP.duelsOrNull();
			return manager == null || !(entity instanceof ServerPlayer player) || manager.allowDeath(player);
		});
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			DuelManager manager = EasyTP.duelsOrNull();
			return manager == null || manager.allowBuilding(player.getUUID());
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			DuelManager manager = EasyTP.duelsOrNull();
			if (level.isClientSide() || manager == null || manager.allowBuilding(player.getUUID())) {
				return InteractionResult.PASS;
			}
			return InteractionResult.FAIL;
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			DuelManager manager = EasyTP.duelsOrNull();
			if (manager != null) {
				manager.onJoin(handler.getPlayer());
			}
		});
	}

	public Lobby lobby() {
		return lobby;
	}

	public boolean inDuel(UUID id) {
		return byPlayer.containsKey(id);
	}

	public boolean allowBuilding(UUID id) {
		Duel duel = byPlayer.get(id);
		return duel == null || (duel.kit.building() && duel.phase == Phase.FIGHT);
	}

	public boolean allowDamage(ServerPlayer player) {
		Duel duel = byPlayer.get(player.getUUID());
		return duel == null || duel.phase == Phase.FIGHT;
	}

	public boolean allowDeath(ServerPlayer player) {
		Duel duel = byPlayer.get(player.getUUID());
		if (duel == null) {
			return true;
		}
		if (holdsTotem(player)) {
			return true;
		}
		player.setHealth(player.getMaxHealth());
		if (duel.phase == Phase.FIGHT) {
			endRound(player.level().getServer(), duel, 1 - duel.sideOf(player.getUUID()));
		}
		return false;
	}

	private static boolean holdsTotem(ServerPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);
			if (stack.has(DataComponents.DEATH_PROTECTION)) {
				return true;
			}
		}
		return false;
	}

	public Msg start(MinecraftServer server, ServerPlayer a, ServerPlayer b, Kits.Kit kit, boolean ranked, int bestOf) {
		ServerLevel level = server.getLevel(DIMENSION);
		if (level == null) {
			return Msg.DUEL_NO_WORLD;
		}
		BoxPvp box = EasyTP.boxOrNull();
		if (box != null && (box.isTagged(a.getUUID()) || box.isTagged(b.getUUID()))) {
			return Msg.DUEL_IN_COMBAT;
		}
		int slot = arenas.acquire(EasyTP.config().duels.maxArenas);
		if (slot < 0) {
			return Msg.DUEL_NO_ARENA;
		}
		try {
			snapshots.save(a.getUUID(), PlayerState.capture(a));
			snapshots.save(b.getUUID(), PlayerState.capture(b));
		} catch (IOException | RuntimeException e) {
			EasyTP.LOGGER.error("EasyTP: nie udało się zapisać stanu graczy przed pojedynkiem", e);
			snapshots.delete(a.getUUID());
			snapshots.delete(b.getUUID());
			arenas.release(slot);
			return Msg.DUEL_SAVE_FAILED;
		}
		Duel duel = new Duel(a, b, kit, slot, ranked, bestOf);
		duels.add(duel);
		for (ServerPlayer player : List.of(a, b)) {
			byPlayer.put(player.getUUID(), duel);
			lobby.removeInvolving(player.getUUID());
			EasyTP.teleports().forget(player.getUUID());
			EasyTP.tpa().removeInvolving(player.getUUID());
		}
		a.sendSystemMessage(Chat.info(Msg.DUEL_START, b.getPlainTextName(), kit.id()));
		b.sendSystemMessage(Chat.info(Msg.DUEL_START, a.getPlainTextName(), kit.id()));
		beginRound(server, duel);
		return null;
	}

	private void beginRound(MinecraftServer server, Duel duel) {
		ServerLevel level = server.getLevel(DIMENSION);
		Arenas.prepare(level, duel.slot, duel.kit.sumo());
		duel.phase = Phase.COUNTDOWN;
		duel.phaseTicks = EasyTP.config().duels.countdownSeconds * 20;
		duel.fightTicks = 0;
		duel.pendingWinner = -1;
		for (int side = 0; side < 2; side++) {
			ServerPlayer player = server.getPlayerList().getPlayer(duel.ids[side]);
			if (player == null) {
				continue;
			}
			preparePlayer(player, level, duel, side);
			player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 20, 5));
		}
	}

	private static void preparePlayer(ServerPlayer player, ServerLevel level, Duel duel, int side) {
		AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
		if (attribute != null) {
			attribute.setBaseValue(20);
		}
		PlayerState.clear(player);
		player.setGameMode(GameType.SURVIVAL);
		PlayerState.teleport(player, level, Arenas.spawn(duel.slot, side, duel.kit.sumo()), Arenas.yaw(side), 0);
		duel.kit.loadout(level.registryAccess()).apply(player);
		if (duel.kit.sumo()) {
			player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, EFFECT_TICKS, 4, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.SATURATION, EFFECT_TICKS, 0, false, false));
		}
		player.setHealth(player.getMaxHealth());
	}

	public void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 == 0) {
			tickLobby(server);
		}
		for (Duel duel : new ArrayList<>(duels)) {
			tickDuel(server, duel);
		}
	}

	private void tickLobby(MinecraftServer server) {
		for (Lobby.Challenge expired : lobby.removeExpired()) {
			ServerPlayer from = server.getPlayerList().getPlayer(expired.from());
			ServerPlayer to = server.getPlayerList().getPlayer(expired.to());
			if (from != null && to != null) {
				from.sendSystemMessage(Chat.info(Msg.DUEL_EXPIRED, to.getPlainTextName()));
			}
		}
		EasyTPConfig.Duels config = EasyTP.config().duels;
		for (Lobby.Match match : lobby.matches(config.queueRange, config.queueRangePerSecond)) {
			ServerPlayer a = server.getPlayerList().getPlayer(match.a());
			ServerPlayer b = server.getPlayerList().getPlayer(match.b());
			Optional<Kits.Kit> kit = Kits.byId(match.kit());
			if (a == null || b == null || kit.isEmpty()) {
				continue;
			}
			Msg error = start(server, a, b, kit.get(), true, 1);
			if (error != null) {
				a.sendSystemMessage(Chat.error(error));
				b.sendSystemMessage(Chat.error(error));
			}
		}
	}

	private void tickDuel(MinecraftServer server, Duel duel) {
		switch (duel.phase) {
			case COUNTDOWN -> tickCountdown(server, duel);
			case FIGHT -> tickFight(server, duel);
			case ROUND_END -> {
				if (--duel.phaseTicks <= 0) {
					afterRound(server, duel);
				}
			}
		}
	}

	private void tickCountdown(MinecraftServer server, Duel duel) {
		for (int side = 0; side < 2; side++) {
			ServerPlayer player = server.getPlayerList().getPlayer(duel.ids[side]);
			if (player == null) {
				continue;
			}
			Vec3 spawn = Arenas.spawn(duel.slot, side, duel.kit.sumo());
			if (player.position().distanceToSqr(spawn) > 1) {
				PlayerState.teleport(player, server.getLevel(DIMENSION), spawn, Arenas.yaw(side), 0);
			}
			if (duel.phaseTicks % 20 == 0 && duel.phaseTicks > 0) {
				player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(Integer.toString(duel.phaseTicks / 20)).withStyle(ChatFormatting.YELLOW)));
			}
		}
		if (--duel.phaseTicks <= 0) {
			duel.phase = Phase.FIGHT;
			for (UUID id : duel.ids) {
				ServerPlayer player = server.getPlayerList().getPlayer(id);
				if (player != null) {
					player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(Msg.DUEL_FIGHT.text()).withStyle(ChatFormatting.RED)));
					player.sendSystemMessage(Chat.ok(Msg.DUEL_FIGHT));
				}
			}
		}
	}

	private void tickFight(MinecraftServer server, Duel duel) {
		duel.fightTicks++;
		for (int side = 0; side < 2; side++) {
			ServerPlayer player = server.getPlayerList().getPlayer(duel.ids[side]);
			if (player != null && (player.getY() < Arenas.FLOOR_Y - FALL_MARGIN || !player.level().dimension().equals(DIMENSION))) {
				endRound(server, duel, 1 - side);
				return;
			}
		}
		if (duel.fightTicks > EasyTP.config().duels.timeLimitSeconds * 20) {
			announce(server, duel, Chat.info(Msg.DUEL_DRAW));
			finish(server, duel, -1, null);
		}
	}

	private void endRound(MinecraftServer server, Duel duel, int winner) {
		if (duel.phase != Phase.FIGHT) {
			return;
		}
		duel.wins[winner]++;
		duel.phase = Phase.ROUND_END;
		duel.phaseTicks = ROUND_END_TICKS;
		duel.pendingWinner = winner;
		if (duel.winsNeeded > 1) {
			announce(server, duel, Chat.info(Msg.DUEL_ROUND_WIN, duel.names[winner], duel.wins[0], duel.wins[1]));
		}
		for (UUID id : duel.ids) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null) {
				player.setHealth(player.getMaxHealth());
				player.clearFire();
				player.removeAllEffects();
			}
		}
	}

	private void afterRound(MinecraftServer server, Duel duel) {
		int winner = duel.pendingWinner;
		if (winner >= 0 && duel.wins[winner] >= duel.winsNeeded) {
			finish(server, duel, winner, null);
		} else {
			beginRound(server, duel);
		}
	}

	private void finish(MinecraftServer server, Duel duel, int winner, UUID leaving) {
		duels.remove(duel);
		byPlayer.remove(duel.ids[0]);
		byPlayer.remove(duel.ids[1]);
		if (winner >= 0) {
			announce(server, duel, Chat.ok(Msg.DUEL_WIN, duel.names[winner]));
			recordResult(server, duel, winner);
		}
		for (UUID id : duel.ids) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null && !id.equals(leaving)) {
				restore(player);
			}
		}
		ServerLevel level = server.getLevel(DIMENSION);
		if (level != null) {
			Arenas.clearEntities(level, duel.slot);
		}
		arenas.release(duel.slot);
	}

	private void recordResult(MinecraftServer server, Duel duel, int winner) {
		PlayerStore players = EasyTP.players();
		PlayerData win = players.touch(duel.ids[winner], duel.names[winner]);
		PlayerData lose = players.touch(duel.ids[1 - winner], duel.names[1 - winner]);
		win.duelWins++;
		lose.duelLosses++;
		if (duel.ranked) {
			int oldWin = win.elo;
			int oldLose = lose.elo;
			Elo.Change change = Elo.after(oldWin, oldLose);
			win.elo = change.winner();
			lose.elo = change.loser();
			notifyElo(server, duel.ids[winner], oldWin, win.elo);
			notifyElo(server, duel.ids[1 - winner], oldLose, lose.elo);
		}
		players.markDirty();
	}

	private static void notifyElo(MinecraftServer server, UUID id, int before, int after) {
		ServerPlayer player = server.getPlayerList().getPlayer(id);
		if (player != null) {
			player.sendSystemMessage(Chat.info(Msg.DUEL_ELO_CHANGE, before, after, Elo.divisionName(after)));
		}
	}

	private static void announce(MinecraftServer server, Duel duel, Component message) {
		for (UUID id : duel.ids) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null) {
				player.sendSystemMessage(message);
			}
		}
	}

	private void restore(ServerPlayer player) {
		Optional<JsonObject> state = snapshots.load(player.getUUID());
		if (state.isEmpty()) {
			player.sendSystemMessage(Chat.error(Msg.DUEL_RESTORE_FAILED));
			EasyTP.LOGGER.error("EasyTP: brak zapisanego stanu gracza {} po pojedynku", player.getPlainTextName());
			PlayerState.clear(player);
			LifeSteal.applyHealth(player, true);
			return;
		}
		try {
			PlayerState.restore(player, state.get(), true);
			LifeSteal.applyHealth(player, false);
			PlayerState.restoreHealth(player, state.get());
			snapshots.delete(player.getUUID());
		} catch (RuntimeException e) {
			EasyTP.LOGGER.error("EasyTP: nie udało się przywrócić stanu gracza {}", player.getPlainTextName(), e);
			player.sendSystemMessage(Chat.error(Msg.DUEL_RESTORE_FAILED));
		}
	}

	public boolean surrender(ServerPlayer player) {
		Duel duel = byPlayer.get(player.getUUID());
		if (duel == null) {
			return false;
		}
		MinecraftServer server = player.level().getServer();
		int side = duel.sideOf(player.getUUID());
		announce(server, duel, Chat.info(Msg.DUEL_SURRENDER, player.getPlainTextName()));
		duel.wins[1 - side] = duel.winsNeeded;
		finish(server, duel, 1 - side, null);
		return true;
	}

	public void onDisconnect(ServerPlayer player) {
		lobby.removeInvolving(player.getUUID());
		Duel duel = byPlayer.get(player.getUUID());
		if (duel == null) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		int side = duel.sideOf(player.getUUID());
		announce(server, duel, Chat.info(Msg.DUEL_LEFT, player.getPlainTextName()));
		duel.wins[1 - side] = duel.winsNeeded;
		finish(server, duel, 1 - side, player.getUUID());
	}

	private void onJoin(ServerPlayer player) {
		if (byPlayer.containsKey(player.getUUID()) || !snapshots.exists(player.getUUID())) {
			return;
		}
		restore(player);
		player.sendSystemMessage(Chat.ok(Msg.DUEL_RESTORED));
	}
}
