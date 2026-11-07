package pl.easytp.lifesteal;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import pl.easytp.Chat;
import pl.easytp.EasyTP;
import pl.easytp.Msg;
import pl.easytp.config.EasyTPConfig;
import pl.easytp.economy.Shop;
import pl.easytp.player.PlayerData;
import pl.easytp.player.PlayerStore;
import pl.easytp.session.Realms;
import pl.easytp.stats.PlayerStats;

import java.util.UUID;

public final class LifeSteal {
	private static final long MINUTE = 60_000L;

	private LifeSteal() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register(LifeSteal::onDeath);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> applyHealth(newPlayer, !alive));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.getPlayer()));
		UseItemCallback.EVENT.register(LifeSteal::onUseItem);
		PlayerBlockBreakEvents.AFTER.register(LifeSteal::onBlockBreak);
	}

	public static int hearts(UUID id) {
		EasyTPConfig.LifeSteal config = EasyTP.config().lifesteal;
		PlayerData data = EasyTP.players().get(id);
		int value = data.hearts < 0 ? config.startHearts : data.hearts;
		value = Math.max(config.minHearts, Math.min(cap(data), value));
		if (value != data.hearts) {
			data.hearts = value;
			EasyTP.players().markDirty();
		}
		return value;
	}

	public static int cap(PlayerData data) {
		return EasyTP.config().lifesteal.maxHearts + data.capBonus;
	}

	public static void setHearts(UUID id, int value) {
		EasyTP.players().get(id).hearts = value;
		EasyTP.players().markDirty();
		hearts(id);
	}

	public static void applyHealth(ServerPlayer player, boolean heal) {
		AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
		if (attribute == null) {
			return;
		}
		double target = EasyTP.config().lifesteal.enabled && Realms.of(player) == Realms.Realm.MAIN ? hearts(player.getUUID()) * 2.0 : 20.0;
		if (attribute.getBaseValue() != target) {
			attribute.setBaseValue(target);
		}
		if (heal || player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	private static void onJoin(ServerPlayer player) {
		EasyTP.players().touch(player.getUUID(), player.getPlainTextName());
		PlayerStats.sync(player);
		applyHealth(player, false);
	}

	private static void onDeath(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof ServerPlayer victim)) {
			return;
		}
		PlayerStats.sync(victim);
		ServerPlayer killer = killerOf(victim, source);
		if (killer == null || killer == victim || Realms.of(victim) != Realms.Realm.MAIN) {
			return;
		}
		PlayerStats.sync(killer);
		EasyTPConfig config = EasyTP.config();
		if (!config.lifesteal.enabled && !config.economy.enabled) {
			return;
		}
		if (EasyTP.clans().sameClan(killer.getUUID(), victim.getUUID())) {
			killer.sendSystemMessage(Chat.info(Msg.KILL_NO_REWARD, victim.getPlainTextName(), Msg.REASON_SAME_CLAN.text()));
			return;
		}
		KillRules rules = EasyTP.killRules();
		KillRules.Verdict verdict = rules.check(killer.getUUID(), victim.getUUID(),
			KillRules.sameIp(killer.getIpAddress(), victim.getIpAddress(), config.lifesteal.ignoreLocalIp),
			victim.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) * 50L, limits(config.lifesteal));
		boolean rewarded = verdict == KillRules.Verdict.ALLOWED;
		rules.recordKill(killer.getUUID(), victim.getUUID(), rewarded);
		if (!rewarded) {
			killer.sendSystemMessage(Chat.info(Msg.KILL_NO_REWARD, victim.getPlainTextName(), reason(verdict).text()));
			return;
		}
		if (config.lifesteal.enabled) {
			stealHeart(killer, victim, config.lifesteal);
		}
		if (config.economy.enabled) {
			payKiller(killer, victim, config.economy);
		}
	}

	private static ServerPlayer killerOf(ServerPlayer victim, DamageSource source) {
		if (source.getEntity() instanceof ServerPlayer direct) {
			return direct;
		}
		return victim.getKillCredit() instanceof ServerPlayer credited ? credited : null;
	}

	private static KillRules.Limits limits(EasyTPConfig.LifeSteal config) {
		return new KillRules.Limits(config.blockSameIp, config.minVictimPlayMinutes * MINUTE,
			config.sameVictimCooldownMinutes * MINUTE, config.recentDeathCooldownMinutes * MINUTE,
			config.killWindowMinutes * MINUTE, config.maxKillsPerWindow);
	}

	private static Msg reason(KillRules.Verdict verdict) {
		return switch (verdict) {
			case SAME_IP -> Msg.REASON_SAME_IP;
			case NEW_PLAYER -> Msg.REASON_NEW_PLAYER;
			case SAME_VICTIM -> Msg.REASON_SAME_VICTIM;
			case RECENT_DEATH -> Msg.REASON_RECENT_DEATH;
			case TOO_MANY_KILLS, ALLOWED -> Msg.REASON_TOO_MANY;
		};
	}

	private static void stealHeart(ServerPlayer killer, ServerPlayer victim, EasyTPConfig.LifeSteal config) {
		int victimHearts = hearts(victim.getUUID());
		if (victimHearts <= config.minHearts) {
			killer.sendSystemMessage(Chat.info(Msg.KILL_VICTIM_MIN, victim.getPlainTextName()));
			return;
		}
		setHearts(victim.getUUID(), victimHearts - 1);
		victim.sendSystemMessage(Chat.error(Msg.VICTIM_LOST, killer.getPlainTextName(), victimHearts - 1));
		int killerHearts = hearts(killer.getUUID());
		if (killerHearts >= cap(EasyTP.players().get(killer.getUUID()))) {
			Shop.give(killer, HeartItems.heart(1));
			killer.sendSystemMessage(Chat.ok(Msg.KILL_HEART_ITEM, victim.getPlainTextName()));
			return;
		}
		setHearts(killer.getUUID(), killerHearts + 1);
		applyHealth(killer, false);
		killer.sendSystemMessage(Chat.ok(Msg.KILL_HEART, victim.getPlainTextName(), killerHearts + 1));
	}

	private static void payKiller(ServerPlayer killer, ServerPlayer victim, EasyTPConfig.Economy config) {
		PlayerStore players = EasyTP.players();
		long stolen = players.get(victim.getUUID()).money * config.killTheftPercent / 100;
		if (stolen > 0 && players.withdraw(victim.getUUID(), stolen)) {
			victim.sendSystemMessage(Chat.error(Msg.VICTIM_ROBBED, Shop.money(stolen)));
		} else {
			stolen = 0;
		}
		long total = stolen + config.killReward;
		if (total > 0) {
			players.deposit(killer.getUUID(), total);
			killer.sendSystemMessage(Chat.ok(Msg.KILL_MONEY, Shop.money(total), Shop.money(stolen)));
		}
	}

	private static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !EasyTP.config().lifesteal.enabled) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (HeartItems.isHeart(stack)) {
			return useHeart(serverPlayer, stack);
		}
		if (HeartItems.isLegendaryHeart(stack)) {
			return useLegendary(serverPlayer, stack);
		}
		return InteractionResult.PASS;
	}

	private static InteractionResult useHeart(ServerPlayer player, ItemStack stack) {
		int hearts = hearts(player.getUUID());
		int cap = cap(EasyTP.players().get(player.getUUID()));
		if (hearts >= cap) {
			player.sendSystemMessage(Chat.error(Msg.HEART_AT_CAP, cap));
			return InteractionResult.FAIL;
		}
		stack.shrink(1);
		setHearts(player.getUUID(), hearts + 1);
		applyHealth(player, false);
		player.sendSystemMessage(Chat.ok(Msg.HEART_USED, hearts + 1));
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult useLegendary(ServerPlayer player, ItemStack stack) {
		PlayerData data = EasyTP.players().get(player.getUUID());
		if (data.capBonus > 0) {
			player.sendSystemMessage(Chat.error(Msg.LEGENDARY_ALREADY, cap(data)));
			return InteractionResult.FAIL;
		}
		stack.shrink(1);
		data.capBonus = EasyTP.config().lifesteal.legendaryHeartBonus;
		EasyTP.players().markDirty();
		player.sendSystemMessage(Chat.ok(Msg.LEGENDARY_USED, cap(data)));
		return InteractionResult.SUCCESS;
	}

	private static void onBlockBreak(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
		EasyTPConfig.LifeSteal config = EasyTP.config().lifesteal;
		if (!config.enabled || config.stoneHeartChance <= 0 || !(player instanceof ServerPlayer serverPlayer) || player.isCreative()
			|| Realms.of(serverPlayer) != Realms.Realm.MAIN) {
			return;
		}
		if ((state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE)) && level.getRandom().nextDouble() < config.stoneHeartChance) {
			Shop.give(serverPlayer, HeartItems.heart(1));
			serverPlayer.sendSystemMessage(Chat.ok(Msg.STONE_HEART));
		}
	}
}
