package pl.easytp.boxpvp;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import pl.easytp.Chat;
import pl.easytp.EasyTP;
import pl.easytp.Msg;
import pl.easytp.config.EasyTPConfig;
import pl.easytp.economy.Shop;
import pl.easytp.gui.ChestGui;
import pl.easytp.lifesteal.KillRules;
import pl.easytp.player.PlayerData;
import pl.easytp.player.PlayerStore;
import pl.easytp.session.PlayerState;
import pl.easytp.session.Realms;
import pl.easytp.util.Cooldowns;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class BoxPvp {
	private static final long MINUTE = 60_000L;
	private static final int RESCUE_Y = 40;
	private static final int SLOT_FIRST_PIECE = 19;

	private final CombatTags tags = new CombatTags(System::currentTimeMillis);
	private final KillRules rules = new KillRules(System::currentTimeMillis);
	private final Cooldowns kitCooldowns = new Cooldowns(System::currentTimeMillis);
	private final Map<UUID, List<ItemStack>> keptGear = new HashMap<>();
	private int mineTicks;
	private int afkTicks;

	private static boolean isBox(Level level) {
		return level.dimension().equals(Realms.BOXPVP_LEVEL);
	}

	private static boolean bypasses(Player player) {
		return player instanceof ServerPlayer serverPlayer && serverPlayer.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	public static void registerEvents() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			BoxPvp box = EasyTP.boxOrNull();
			return box == null || !isBox(entity.level()) || !(entity instanceof ServerPlayer player) || allowDamage(player, source);
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			BoxPvp box = EasyTP.boxOrNull();
			if (box != null && isBox(entity.level()) && entity instanceof ServerPlayer victim && source.getEntity() instanceof ServerPlayer attacker && attacker != victim) {
				box.tagBoth(victim, attacker);
			}
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			BoxPvp box = EasyTP.boxOrNull();
			if (box != null && isBox(entity.level()) && entity instanceof ServerPlayer player) {
				box.beforeDeath(player);
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			BoxPvp box = EasyTP.boxOrNull();
			if (box != null && isBox(entity.level()) && entity instanceof ServerPlayer player) {
				box.afterDeath(player, source);
			}
		});
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			BoxPvp box = EasyTP.boxOrNull();
			if (box == null || !isBox(level) || bypasses(player)) {
				return true;
			}
			if (BoxMap.inMineVolume(pos) && player instanceof ServerPlayer serverPlayer) {
				autosell((ServerLevel) level, serverPlayer, pos, state);
			}
			return false;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || EasyTP.boxOrNull() == null || !isBox(level) || bypasses(player)) {
				return InteractionResult.PASS;
			}
			return InteractionResult.FAIL;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			BoxPvp box = EasyTP.boxOrNull();
			if (level.isClientSide() || box == null || !isBox(level) || bypasses(player)) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			boolean pearl = stack.getItem() instanceof EnderpearlItem && box.tags.isTagged(player.getUUID());
			return pearl || stack.getItem() instanceof BucketItem ? InteractionResult.FAIL : InteractionResult.PASS;
		});
	}

	private static boolean allowDamage(ServerPlayer victim, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer attacker)) {
			return true;
		}
		return BoxMap.zoneAt(victim.getX(), victim.getZ()) == BoxMap.Zone.PVP
			&& BoxMap.zoneAt(attacker.getX(), attacker.getZ()) == BoxMap.Zone.PVP;
	}

	private void tagBoth(ServerPlayer victim, ServerPlayer attacker) {
		long millis = EasyTP.config().boxpvp.combatTagSeconds * 1000L;
		tags.tag(victim.getUUID(), attacker.getUUID(), millis);
		tags.tag(attacker.getUUID(), victim.getUUID(), millis);
	}

	public boolean isTagged(UUID id) {
		return tags.isTagged(id);
	}

	public int tagSeconds(UUID id) {
		return (int) Math.ceil(tags.remainingMillis(id) / 1000.0);
	}

	private void beforeDeath(ServerPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			if (player.getItemInHand(hand).has(DataComponents.DEATH_PROTECTION)) {
				return;
			}
		}
		int percent = EasyTP.config().boxpvp.keepGearPercent;
		if (percent <= 0) {
			return;
		}
		List<ItemStack> kept = new ArrayList<>();
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty() && Gear.pieceOf(stack).isPresent() && player.getRandom().nextInt(100) < percent) {
				kept.add(stack.copy());
				inventory.setItem(slot, ItemStack.EMPTY);
			}
		}
		if (!kept.isEmpty()) {
			keptGear.computeIfAbsent(player.getUUID(), key -> new ArrayList<>()).addAll(kept);
		}
	}

	private void afterDeath(ServerPlayer victim, DamageSource source) {
		MinecraftServer server = victim.level().getServer();
		PlayerStore players = EasyTP.players();
		PlayerData victimData = players.touch(victim.getUUID(), victim.getPlainTextName());
		victimData.boxDeaths++;
		victimData.boxStreak = 0;
		players.markDirty();
		UUID killerId = source.getEntity() instanceof ServerPlayer direct ? direct.getUUID() : tags.attackerOf(victim.getUUID()).orElse(null);
		tags.clear(victim.getUUID());
		ServerPlayer killer = killerId == null ? null : server.getPlayerList().getPlayer(killerId);
		if (killer == null || killer == victim) {
			return;
		}
		tags.clear(killer.getUUID());
		EasyTPConfig.BoxPvpConfig config = EasyTP.config().boxpvp;
		KillRules.Limits limits = new KillRules.Limits(config.blockSameIp, config.minVictimPlayMinutes * MINUTE,
			config.sameVictimCooldownMinutes * MINUTE, config.recentDeathCooldownMinutes * MINUTE, 60 * MINUTE, Integer.MAX_VALUE);
		KillRules.Verdict verdict = rules.check(killer.getUUID(), victim.getUUID(), KillRules.sameIp(killer.getIpAddress(), victim.getIpAddress(), config.ignoreLocalIp),
			victim.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) * 50L, limits);
		boolean rewarded = verdict == KillRules.Verdict.ALLOWED;
		rules.recordKill(killer.getUUID(), victim.getUUID(), rewarded);
		if (!rewarded) {
			killer.sendSystemMessage(Chat.info(Msg.BOX_KILL_NO_REWARD, victim.getPlainTextName()));
			return;
		}
		PlayerData killerData = players.touch(killer.getUUID(), killer.getPlainTextName());
		killerData.boxKills++;
		killerData.boxStreak++;
		killerData.boxBestStreak = Math.max(killerData.boxBestStreak, killerData.boxStreak);
		players.deposit(killer.getUUID(), config.killReward);
		players.markDirty();
		killer.sendSystemMessage(Chat.ok(Msg.BOX_KILL, victim.getPlainTextName(), Shop.money(config.killReward), killerData.boxStreak));
	}

	private static void autosell(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
		long price = BoxMap.priceOf(state);
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		if (price <= 0) {
			return;
		}
		int stars = Gear.pieceOf(player.getMainHandItem()).filter(piece -> piece == Gear.Piece.PICKAXE).isPresent() ? Gear.starsOf(player.getMainHandItem()) : 0;
		long earned = Math.max(1, Math.round(price * Gear.miningBonus(stars)));
		EasyTP.players().deposit(player.getUUID(), earned);
		player.sendSystemMessage(Component.literal(Msg.BOX_SOLD.text(Shop.money(earned))).withStyle(ChatFormatting.GREEN), true);
	}

	public Optional<Spot> spawn(MinecraftServer server) {
		return spot(server, BoxMap.Zone.SPAWN);
	}

	public record Spot(ServerLevel level, Vec3 pos) {
	}

	public Optional<Spot> spot(MinecraftServer server, BoxMap.Zone zone) {
		ServerLevel level = server.getLevel(Realms.BOXPVP_LEVEL);
		if (level == null) {
			return Optional.empty();
		}
		BoxMap.ensureBuilt(level, level.getRandom());
		return Optional.of(new Spot(level, zone.spawn()));
	}

	public void onRespawn(ServerPlayer player) {
		List<ItemStack> kept = keptGear.remove(player.getUUID());
		if (kept != null) {
			kept.forEach(stack -> Shop.give(player, stack));
			player.sendSystemMessage(Chat.info(Msg.BOX_KEPT, kept.size()));
		}
	}

	public void onDisconnect(ServerPlayer player) {
		if (tags.isTagged(player.getUUID()) && isBox(player.level()) && player.isAlive()) {
			EasyTP.LOGGER.info("EasyTP: {} wychodzi w trakcie walki w BoxPvP", player.getPlainTextName());
			player.kill(player.level());
		}
		tags.clear(player.getUUID());
	}

	public boolean claimKit(ServerPlayer player) {
		EasyTPConfig.BoxPvpConfig config = EasyTP.config().boxpvp;
		UUID id = player.getUUID();
		long left = kitCooldowns.remainingMillis(id);
		if (left > 0) {
			player.sendSystemMessage(Chat.error(Msg.BOX_KIT_COOLDOWN, (int) Math.ceil(left / 1000.0)));
			return false;
		}
		RegistryAccess registries = player.level().registryAccess();
		for (Gear.Piece piece : Gear.Piece.values()) {
			if (Gear.findSlot(player, piece) >= 0) {
				continue;
			}
			ItemStack stack = Gear.build(piece, 0, registries);
			if (piece.slot != null && player.getItemBySlot(piece.slot).isEmpty()) {
				player.setItemSlot(piece.slot, stack);
			} else {
				Shop.give(player, stack);
			}
		}
		Shop.give(player, new ItemStack(Items.GOLDEN_APPLE, 4));
		Shop.give(player, new ItemStack(Items.COOKED_BEEF, 16));
		kitCooldowns.start(id, config.kitCooldownSeconds * 1000L);
		player.sendSystemMessage(Chat.ok(Msg.BOX_KIT));
		return true;
	}

	public void openUpgrades(ServerPlayer player) {
		EasyTPConfig.BoxPvpConfig config = EasyTP.config().boxpvp;
		Map<Integer, ChestGui.Entry> slots = new HashMap<>();
		int index = 0;
		for (Gear.Piece piece : Gear.Piece.values()) {
			int inventorySlot = Gear.findSlot(player, piece);
			ItemStack icon;
			if (inventorySlot < 0) {
				icon = ChestGui.icon(piece.item(0), piece.title.text(), ChatFormatting.GRAY, Msg.BOX_NO_PIECE.text());
			} else {
				ItemStack current = player.getInventory().getItem(inventorySlot);
				int stars = Gear.starsOf(current);
				icon = current.copy();
				if (stars >= Gear.MAX_STARS) {
					ChestGui.withLore(icon, Msg.BOX_MAX_STARS.text());
				} else {
					ChestGui.withLore(icon, Msg.BOX_UPGRADE_COST.text(Shop.money(Gear.cost(config.upgradeBaseCost, stars))),
						Msg.BOX_UPGRADE_CHANCE.text(Gear.chance(config.upgradeBaseChance, config.upgradeChancePenalty, stars) + "%"));
				}
			}
			slots.put(SLOT_FIRST_PIECE + index, new ChestGui.Entry(icon, p -> upgrade(p, piece)));
			index++;
		}
		ChestGui.open(player, Component.literal(Msg.BOX_UPGRADE_TITLE.text()), slots);
	}

	private void upgrade(ServerPlayer player, Gear.Piece piece) {
		EasyTPConfig.BoxPvpConfig config = EasyTP.config().boxpvp;
		int slot = Gear.findSlot(player, piece);
		if (slot < 0) {
			player.sendSystemMessage(Chat.error(Msg.BOX_NO_PIECE));
			return;
		}
		ItemStack current = player.getInventory().getItem(slot);
		int stars = Gear.starsOf(current);
		if (stars >= Gear.MAX_STARS) {
			player.sendSystemMessage(Chat.info(Msg.BOX_MAX_STARS));
			return;
		}
		long cost = Gear.cost(config.upgradeBaseCost, stars);
		PlayerStore players = EasyTP.players();
		if (!players.withdraw(player.getUUID(), cost)) {
			player.sendSystemMessage(Chat.error(Msg.NO_MONEY, Shop.money(cost), Shop.money(players.get(player.getUUID()).money)));
			return;
		}
		if (player.getRandom().nextInt(100) < Gear.chance(config.upgradeBaseChance, config.upgradeChancePenalty, stars)) {
			player.getInventory().setItem(slot, Gear.build(piece, stars + 1, player.level().registryAccess()));
			player.sendSystemMessage(Chat.ok(Msg.BOX_UPGRADED, piece.title.text(), stars + 1));
		} else {
			player.sendSystemMessage(Chat.error(Msg.BOX_UPGRADE_FAILED, Shop.money(cost)));
		}
		openUpgrades(player);
	}

	public void openWarps(ServerPlayer player) {
		Map<Integer, ChestGui.Entry> slots = new HashMap<>();
		slots.put(19, warpEntry(Items.QUARTZ_BLOCK, Msg.BOX_WARP_SPAWN, BoxMap.Zone.SPAWN));
		slots.put(21, warpEntry(Items.DIAMOND_PICKAXE, Msg.BOX_WARP_MINE, BoxMap.Zone.MINE));
		slots.put(23, warpEntry(Items.IRON_SWORD, Msg.BOX_WARP_PVP, BoxMap.Zone.PVP));
		slots.put(25, warpEntry(Items.CLOCK, Msg.BOX_WARP_AFK, BoxMap.Zone.AFK));
		ChestGui.open(player, Component.literal(Msg.BOX_WARPS_TITLE.text()), slots);
	}

	private ChestGui.Entry warpEntry(Item icon, Msg name, BoxMap.Zone zone) {
		return new ChestGui.Entry(ChestGui.icon(icon, name.text(), ChatFormatting.YELLOW), player -> warp(player, zone, name));
	}

	private void warp(ServerPlayer player, BoxMap.Zone zone, Msg name) {
		if (!isBox(player.level())) {
			player.sendSystemMessage(Chat.error(Msg.BOX_ONLY_INSIDE));
			return;
		}
		if (tags.isTagged(player.getUUID())) {
			player.sendSystemMessage(Chat.error(Msg.BOX_TAGGED, tagSeconds(player.getUUID())));
			return;
		}
		player.closeContainer();
		PlayerState.teleport(player, (ServerLevel) player.level(), zone.spawn(), 0, 0);
		player.sendSystemMessage(Chat.ok(Msg.BOX_WARPED, name.text()));
	}

	public void tick(MinecraftServer server) {
		ServerLevel level = server.getLevel(Realms.BOXPVP_LEVEL);
		if (level == null || !EasyTP.config().boxpvp.enabled) {
			return;
		}
		if (server.getTickCount() % 20 == 0) {
			tickTags(server);
		}
		List<ServerPlayer> players = level.players();
		if (players.isEmpty()) {
			return;
		}
		for (ServerPlayer player : List.copyOf(players)) {
			if (player.getY() < RESCUE_Y && player.isAlive()) {
				PlayerState.teleport(player, level, BoxMap.Zone.SPAWN.spawn(), 0, 0);
			}
		}
		EasyTPConfig.BoxPvpConfig config = EasyTP.config().boxpvp;
		if (++mineTicks >= config.mineResetSeconds * 20) {
			mineTicks = 0;
			BoxMap.ensureBuilt(level, level.getRandom());
			BoxMap.resetMine(level, level.getRandom());
			for (ServerPlayer player : players) {
				if (BoxMap.zoneAt(player.getX(), player.getZ()) == BoxMap.Zone.MINE && player.getY() <= BoxMap.MINE_TOP + 1) {
					player.teleportTo(player.getX(), BoxMap.MINE_TOP + 1, player.getZ());
				}
				if (BoxMap.zoneAt(player.getX(), player.getZ()) == BoxMap.Zone.MINE) {
					player.sendSystemMessage(Chat.info(Msg.BOX_MINE_RESET));
				}
			}
		}
		if (++afkTicks >= config.afkIntervalSeconds * 20) {
			afkTicks = 0;
			for (ServerPlayer player : players) {
				if (BoxMap.zoneAt(player.getX(), player.getZ()) == BoxMap.Zone.AFK && config.afkReward > 0) {
					EasyTP.players().deposit(player.getUUID(), config.afkReward);
					player.sendSystemMessage(Chat.ok(Msg.BOX_AFK_REWARD, Shop.money(config.afkReward)));
				}
			}
		}
	}

	private void tickTags(MinecraftServer server) {
		for (UUID id : tags.removeExpired()) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null) {
				player.sendSystemMessage(Chat.ok(Msg.BOX_TAG_ENDED));
			}
		}
		for (UUID id : tags.taggedPlayers()) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null) {
				player.sendSystemMessage(Component.literal(Msg.BOX_TAG_BAR.text(tagSeconds(id))).withStyle(ChatFormatting.RED), true);
			}
		}
	}
}
