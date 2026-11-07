package pl.easytp.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class MarketStore {
	private static final Logger LOGGER = LoggerFactory.getLogger("easytp");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public record Listing(long id, UUID seller, String sellerName, long price, JsonElement item) {
	}

	private final Path file;
	private final Map<Long, Listing> listings = new LinkedHashMap<>();
	private long nextId = 1;

	public MarketStore(Path file) {
		this.file = file;
	}

	public Listing add(UUID seller, String sellerName, long price, JsonElement item) {
		Listing listing = new Listing(nextId++, seller, sellerName, price, item);
		listings.put(listing.id(), listing);
		persist();
		return listing;
	}

	public Optional<Listing> remove(long id) {
		Listing removed = listings.remove(id);
		if (removed == null) {
			return Optional.empty();
		}
		persist();
		return Optional.of(removed);
	}

	public Optional<Listing> find(long id) {
		return Optional.ofNullable(listings.get(id));
	}

	public List<Listing> all() {
		List<Listing> result = new ArrayList<>(listings.values());
		java.util.Collections.reverse(result);
		return result;
	}

	public int countOf(UUID seller) {
		int count = 0;
		for (Listing listing : listings.values()) {
			if (listing.seller().equals(seller)) {
				count++;
			}
		}
		return count;
	}

	public Optional<Path> load() throws IOException {
		listings.clear();
		nextId = 1;
		if (!Files.exists(file)) {
			return Optional.empty();
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			if (root.has("listings") && root.get("listings").isJsonArray()) {
				for (JsonElement element : root.getAsJsonArray("listings")) {
					try {
						JsonObject o = element.getAsJsonObject();
						long id = o.get("id").getAsLong();
						listings.put(id, new Listing(id, UUID.fromString(o.get("seller").getAsString()),
							o.get("sellerName").getAsString(), o.get("price").getAsLong(), o.get("item")));
						nextId = Math.max(nextId, id + 1);
					} catch (RuntimeException e) {
						LOGGER.warn("EasyTP: pomijam uszkodzoną ofertę rynku: {}", element);
					}
				}
			}
			return Optional.empty();
		} catch (JsonParseException | IllegalStateException e) {
			listings.clear();
			Path backup = file.resolveSibling(file.getFileName() + ".broken-" + System.currentTimeMillis());
			Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
			LOGGER.error("EasyTP: plik {} jest uszkodzony, zapisano go jako {}", file, backup, e);
			return Optional.of(backup);
		}
	}

	public void save() throws IOException {
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		JsonArray array = new JsonArray();
		for (Listing listing : listings.values()) {
			JsonObject o = new JsonObject();
			o.addProperty("id", listing.id());
			o.addProperty("seller", listing.seller().toString());
			o.addProperty("sellerName", listing.sellerName());
			o.addProperty("price", listing.price());
			o.add("item", listing.item());
			array.add(o);
		}
		root.add("listings", array);
		Path parent = file.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
			GSON.toJson(root, writer);
		}
		try {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private void persist() {
		try {
			save();
		} catch (IOException e) {
			LOGGER.error("EasyTP: nie udało się zapisać pliku {}", file, e);
		}
	}
}
