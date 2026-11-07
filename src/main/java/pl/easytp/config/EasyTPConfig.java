package pl.easytp.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EasyTPConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("easytp");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static final class LifeSteal {
		public boolean enabled = true;
		public int startHearts = 10;
		public int minHearts = 1;
		public int maxHearts = 30;
		public int legendaryHeartBonus = 10;
		public boolean blockSameIp = true;
		public boolean ignoreLocalIp = true;
		public int minVictimPlayMinutes = 30;
		public int sameVictimCooldownMinutes = 30;
		public int recentDeathCooldownMinutes = 3;
		public int killWindowMinutes = 60;
		public int maxKillsPerWindow = 15;
		public double stoneHeartChance = 0.0001;
		public int legendaryHeartPrice = 15;
		public int legendarySetPrice = 20;

		void sanitize() {
			maxHearts = clamp(maxHearts, 1, 200);
			legendaryHeartBonus = clamp(legendaryHeartBonus, 0, 200);
			minHearts = clamp(minHearts, 1, maxHearts);
			startHearts = clamp(startHearts, minHearts, maxHearts);
			minVictimPlayMinutes = clamp(minVictimPlayMinutes, 0, 100000);
			sameVictimCooldownMinutes = clamp(sameVictimCooldownMinutes, 0, 100000);
			recentDeathCooldownMinutes = clamp(recentDeathCooldownMinutes, 0, 100000);
			killWindowMinutes = clamp(killWindowMinutes, 1, 100000);
			maxKillsPerWindow = clamp(maxKillsPerWindow, 1, 100000);
			stoneHeartChance = Double.isNaN(stoneHeartChance) ? 0 : Math.max(0, Math.min(1, stoneHeartChance));
			legendaryHeartPrice = clamp(legendaryHeartPrice, 1, 100000);
			legendarySetPrice = clamp(legendarySetPrice, 1, 100000);
		}
	}

	public static final class Clans {
		public boolean enabled = true;
		public int maxMembers = 20;
		public int inviteSeconds = 300;
		public long createCost = 0;

		void sanitize() {
			maxMembers = clamp(maxMembers, 1, 500);
			inviteSeconds = clamp(inviteSeconds, 1, 86400);
			createCost = Math.max(0, createCost);
		}
	}

	public static final class Duels {
		public boolean enabled = true;
		public int maxArenas = 16;
		public int timeLimitSeconds = 600;
		public int countdownSeconds = 3;
		public int challengeSeconds = 60;
		public int queueRange = 100;
		public int queueRangePerSecond = 10;

		void sanitize() {
			maxArenas = clamp(maxArenas, 1, 256);
			timeLimitSeconds = clamp(timeLimitSeconds, 5, 7200);
			countdownSeconds = clamp(countdownSeconds, 1, 30);
			challengeSeconds = clamp(challengeSeconds, 1, 3600);
			queueRange = clamp(queueRange, 0, 100000);
			queueRangePerSecond = clamp(queueRangePerSecond, 0, 100000);
		}
	}

	public static final class OneBlockConfig {
		public boolean enabled = true;
		public int inviteSeconds = 300;
		public long levelRewardPerLevel = 100;

		void sanitize() {
			inviteSeconds = clamp(inviteSeconds, 1, 86400);
			levelRewardPerLevel = Math.max(0, levelRewardPerLevel);
		}
	}

	public static final class BoxPvpConfig {
		public boolean enabled = true;
		public int combatTagSeconds = 20;
		public int keepGearPercent = 10;
		public long killReward = 100;
		public boolean blockSameIp = true;
		public boolean ignoreLocalIp = true;
		public int minVictimPlayMinutes = 10;
		public int sameVictimCooldownMinutes = 30;
		public int recentDeathCooldownMinutes = 3;
		public int kitCooldownSeconds = 300;
		public long upgradeBaseCost = 500;
		public int upgradeBaseChance = 90;
		public int upgradeChancePenalty = 7;
		public int mineResetSeconds = 300;
		public int afkIntervalSeconds = 60;
		public long afkReward = 20;

		void sanitize() {
			combatTagSeconds = clamp(combatTagSeconds, 1, 600);
			keepGearPercent = clamp(keepGearPercent, 0, 100);
			killReward = Math.max(0, killReward);
			minVictimPlayMinutes = clamp(minVictimPlayMinutes, 0, 100000);
			sameVictimCooldownMinutes = clamp(sameVictimCooldownMinutes, 0, 100000);
			recentDeathCooldownMinutes = clamp(recentDeathCooldownMinutes, 0, 100000);
			kitCooldownSeconds = clamp(kitCooldownSeconds, 0, 86400);
			upgradeBaseCost = Math.max(0, upgradeBaseCost);
			upgradeBaseChance = clamp(upgradeBaseChance, 0, 100);
			upgradeChancePenalty = clamp(upgradeChancePenalty, 0, 100);
			mineResetSeconds = clamp(mineResetSeconds, 5, 86400);
			afkIntervalSeconds = clamp(afkIntervalSeconds, 1, 86400);
			afkReward = Math.max(0, afkReward);
		}
	}

	public static final class ShopOffer {
		public String item;
		public int count = 1;
		public long price;

		public ShopOffer() {
		}

		ShopOffer(String item, int count, long price) {
			this.item = item;
			this.count = count;
			this.price = price;
		}
	}

	public static final class Economy {
		public boolean enabled = true;
		public String currency = "$";
		public long killReward = 50;
		public int killTheftPercent = 10;
		public int marketMaxListings = 10;
		public long marketMaxPrice = 1_000_000_000L;
		public Map<String, Long> sellPrices = defaultSellPrices();
		public List<ShopOffer> shop = defaultShop();

		void sanitize() {
			if (currency == null || currency.isBlank()) {
				currency = "$";
			}
			killReward = Math.max(0, killReward);
			killTheftPercent = clamp(killTheftPercent, 0, 100);
			marketMaxListings = clamp(marketMaxListings, 1, 1000);
			marketMaxPrice = Math.max(1, Math.min(1_000_000_000_000L, marketMaxPrice));
			if (sellPrices == null) {
				sellPrices = defaultSellPrices();
			}
			if (shop == null) {
				shop = defaultShop();
			}
		}

		private static Map<String, Long> defaultSellPrices() {
			Map<String, Long> prices = new LinkedHashMap<>();
			prices.put("minecraft:cobblestone", 1L);
			prices.put("minecraft:coal", 3L);
			prices.put("minecraft:copper_ingot", 4L);
			prices.put("minecraft:iron_ingot", 12L);
			prices.put("minecraft:gold_ingot", 18L);
			prices.put("minecraft:redstone", 3L);
			prices.put("minecraft:lapis_lazuli", 5L);
			prices.put("minecraft:emerald", 40L);
			prices.put("minecraft:diamond", 100L);
			prices.put("minecraft:netherite_ingot", 1500L);
			return prices;
		}

		private static List<ShopOffer> defaultShop() {
			List<ShopOffer> offers = new ArrayList<>();
			offers.add(new ShopOffer("minecraft:cooked_beef", 16, 40));
			offers.add(new ShopOffer("minecraft:golden_apple", 1, 150));
			offers.add(new ShopOffer("minecraft:enchanted_golden_apple", 1, 3000));
			offers.add(new ShopOffer("minecraft:ender_pearl", 4, 200));
			offers.add(new ShopOffer("minecraft:totem_of_undying", 1, 2500));
			offers.add(new ShopOffer("minecraft:experience_bottle", 16, 300));
			offers.add(new ShopOffer("minecraft:obsidian", 8, 160));
			offers.add(new ShopOffer("minecraft:tnt", 4, 200));
			offers.add(new ShopOffer("minecraft:arrow", 32, 60));
			offers.add(new ShopOffer("minecraft:diamond", 1, 250));
			return offers;
		}
	}

	public String language = "pl";
	public int maxHomes = 3;
	public int tpaTimeoutSeconds = 60;
	public int cooldownSeconds = 3;
	public int warmupSeconds = 0;
	public boolean opsBypassLimits = true;
	public int maxPunishmentDays = 365;
	public boolean backupEnabled = true;
	public int backupIntervalMinutes = 180;
	public int backupKeep = 24;
	public String backupDir = "backups";
	public LifeSteal lifesteal = new LifeSteal();
	public Economy economy = new Economy();
	public Clans clans = new Clans();
	public Duels duels = new Duels();
	public OneBlockConfig oneblock = new OneBlockConfig();
	public BoxPvpConfig boxpvp = new BoxPvpConfig();

	public static EasyTPConfig load(Path file) throws IOException {
		EasyTPConfig config = null;
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				config = GSON.fromJson(reader, EasyTPConfig.class);
			} catch (JsonParseException e) {
				Path backup = file.resolveSibling(file.getFileName() + ".broken-" + System.currentTimeMillis());
				Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
				LOGGER.error("EasyTP: plik konfiguracji {} jest uszkodzony, zapisano go jako {}; używam ustawień domyślnych", file, backup, e);
			}
		}
		if (config == null) {
			config = new EasyTPConfig();
		}
		config.sanitize();
		config.save(file);
		return config;
	}

	public void save(Path file) throws IOException {
		Path parent = file.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			GSON.toJson(this, writer);
		}
	}

	void sanitize() {
		if (language == null || !(language.equalsIgnoreCase("pl") || language.equalsIgnoreCase("en"))) {
			language = "pl";
		}
		language = language.toLowerCase(java.util.Locale.ROOT);
		maxHomes = clamp(maxHomes, 1, 100);
		tpaTimeoutSeconds = clamp(tpaTimeoutSeconds, 5, 600);
		cooldownSeconds = clamp(cooldownSeconds, 0, 3600);
		warmupSeconds = clamp(warmupSeconds, 0, 30);
		maxPunishmentDays = clamp(maxPunishmentDays, 1, 3650);
		backupIntervalMinutes = clamp(backupIntervalMinutes, 1, 1440);
		backupKeep = clamp(backupKeep, 1, 1000);
		if (backupDir == null || backupDir.isBlank()) {
			backupDir = "backups";
		}
		backupDir = backupDir.trim();
		if (lifesteal == null) {
			lifesteal = new LifeSteal();
		}
		lifesteal.sanitize();
		if (economy == null) {
			economy = new Economy();
		}
		economy.sanitize();
		if (clans == null) {
			clans = new Clans();
		}
		clans.sanitize();
		if (duels == null) {
			duels = new Duels();
		}
		duels.sanitize();
		if (oneblock == null) {
			oneblock = new OneBlockConfig();
		}
		oneblock.sanitize();
		if (boxpvp == null) {
			boxpvp = new BoxPvpConfig();
		}
		boxpvp.sanitize();
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
