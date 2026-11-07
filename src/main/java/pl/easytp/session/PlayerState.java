package pl.easytp.session;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public final class PlayerState {
	private PlayerState() {
	}

	public static JsonObject capture(ServerPlayer player) {
		RegistryOps<JsonElement> ops = ops(player);
		JsonObject state = new JsonObject();
		state.add("items", writeContainer(player.getInventory(), ops));
		state.add("enderChest", writeContainer(player.getEnderChestInventory(), ops));
		state.addProperty("selected", player.getInventory().getSelectedSlot());
		state.addProperty("health", player.getHealth());
		state.addProperty("absorption", player.getAbsorptionAmount());
		state.addProperty("food", player.getFoodData().getFoodLevel());
		state.addProperty("saturation", player.getFoodData().getSaturationLevel());
		state.addProperty("xpLevel", player.experienceLevel);
		state.addProperty("xpProgress", player.experienceProgress);
		state.addProperty("xpTotal", player.totalExperience);
		state.addProperty("gameMode", player.gameMode().getName());
		JsonArray effects = new JsonArray();
		for (MobEffectInstance effect : player.getActiveEffects()) {
			effects.add(MobEffectInstance.CODEC.encodeStart(ops, effect).getOrThrow());
		}
		state.add("effects", effects);
		state.addProperty("dimension", player.level().dimension().identifier().toString());
		state.addProperty("x", player.getX());
		state.addProperty("y", player.getY());
		state.addProperty("z", player.getZ());
		state.addProperty("yaw", player.getYRot());
		state.addProperty("pitch", player.getXRot());
		return state;
	}

	public static void restore(ServerPlayer player, JsonObject state, boolean teleport) {
		RegistryOps<JsonElement> ops = ops(player);
		clear(player);
		readContainer(player.getInventory(), state.getAsJsonArray("items"), ops);
		readContainer(player.getEnderChestInventory(), state.getAsJsonArray("enderChest"), ops);
		player.getInventory().setSelectedSlot(state.get("selected").getAsInt());
		player.experienceLevel = state.get("xpLevel").getAsInt();
		player.experienceProgress = state.get("xpProgress").getAsFloat();
		player.totalExperience = state.get("xpTotal").getAsInt();
		player.connection.send(new ClientboundSetExperiencePacket(player.experienceProgress, player.totalExperience, player.experienceLevel));
		player.setGameMode(GameType.byName(state.get("gameMode").getAsString()));
		for (JsonElement effect : state.getAsJsonArray("effects")) {
			MobEffectInstance.CODEC.parse(ops, effect).result().ifPresent(player::addEffect);
		}
		if (teleport) {
			teleportBack(player, state);
		}
		player.getFoodData().setFoodLevel(state.get("food").getAsInt());
		player.getFoodData().setSaturation(state.get("saturation").getAsFloat());
		player.setAbsorptionAmount(state.get("absorption").getAsFloat());
		player.setHealth(state.get("health").getAsFloat());
		player.getInventory().setChanged();
	}

	public static void restoreHealth(ServerPlayer player, JsonObject state) {
		player.setHealth(state.get("health").getAsFloat());
	}

	public static void clear(ServerPlayer player) {
		player.closeContainer();
		player.stopRiding();
		player.getInventory().clearContent();
		player.containerMenu.setCarried(ItemStack.EMPTY);
		player.getEnderChestInventory().clearContent();
		player.removeAllEffects();
		player.clearFire();
		player.setTicksFrozen(0);
		player.resetFallDistance();
		player.setAbsorptionAmount(0);
		player.experienceLevel = 0;
		player.experienceProgress = 0;
		player.totalExperience = 0;
		player.connection.send(new ClientboundSetExperiencePacket(0, 0, 0));
		player.getFoodData().setFoodLevel(20);
		player.getFoodData().setSaturation(5);
		player.setHealth(player.getMaxHealth());
	}

	public static void teleport(ServerPlayer player, ServerLevel level, Vec3 pos, float yaw, float pitch) {
		player.stopRiding();
		player.resetFallDistance();
		player.teleport(new TeleportTransition(level, pos, Vec3.ZERO, yaw, pitch, TeleportTransition.DO_NOTHING));
	}

	public static void teleportToWorldSpawn(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		LevelData.RespawnData respawn = server.getRespawnData();
		ServerLevel level = server.getLevel(respawn.dimension());
		teleport(player, level == null ? server.overworld() : level, Vec3.atBottomCenterOf(respawn.pos()), respawn.yaw(), 0);
	}

	private static void teleportBack(ServerPlayer player, JsonObject state) {
		Identifier id = Identifier.tryParse(state.get("dimension").getAsString());
		ServerLevel level = id == null ? null : player.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, id));
		if (level == null) {
			level = player.level().getServer().overworld();
		}
		Vec3 pos = new Vec3(state.get("x").getAsDouble(), state.get("y").getAsDouble(), state.get("z").getAsDouble());
		teleport(player, level, pos, state.get("yaw").getAsFloat(), state.get("pitch").getAsFloat());
	}

	private static JsonArray writeContainer(Container container, RegistryOps<JsonElement> ops) {
		JsonArray items = new JsonArray();
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (!stack.isEmpty()) {
				JsonObject entry = new JsonObject();
				entry.addProperty("slot", slot);
				entry.add("item", ItemStack.CODEC.encodeStart(ops, stack).getOrThrow());
				items.add(entry);
			}
		}
		return items;
	}

	private static void readContainer(Container container, JsonArray items, RegistryOps<JsonElement> ops) {
		for (JsonElement element : items) {
			JsonObject entry = element.getAsJsonObject();
			int slot = entry.get("slot").getAsInt();
			Optional<ItemStack> stack = ItemStack.CODEC.parse(ops, entry.get("item")).result();
			if (stack.isPresent() && slot >= 0 && slot < container.getContainerSize()) {
				container.setItem(slot, stack.get());
			}
		}
	}

	private static RegistryOps<JsonElement> ops(ServerPlayer player) {
		return RegistryOps.create(JsonOps.INSTANCE, player.level().registryAccess());
	}
}
