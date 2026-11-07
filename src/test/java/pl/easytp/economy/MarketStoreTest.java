package pl.easytp.economy;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketStoreTest {
	@TempDir
	Path dir;

	private static JsonObject item(String id) {
		JsonObject item = new JsonObject();
		item.addProperty("id", id);
		item.addProperty("count", 3);
		return item;
	}

	private MarketStore store() {
		return new MarketStore(dir.resolve("market.json"));
	}

	@Test
	void addFindRemove() {
		MarketStore store = store();
		UUID seller = UUID.randomUUID();
		MarketStore.Listing first = store.add(seller, "Ala", 10, item("minecraft:dirt"));
		MarketStore.Listing second = store.add(seller, "Ala", 20, item("minecraft:stone"));
		assertEquals(2, store.countOf(seller));
		assertEquals(0, store.countOf(UUID.randomUUID()));
		assertEquals(second.id(), store.all().get(0).id());
		assertTrue(store.remove(first.id()).isPresent());
		assertTrue(store.remove(first.id()).isEmpty());
		assertTrue(store.find(first.id()).isEmpty());
		assertEquals(1, store.countOf(seller));
	}

	@Test
	void persistsAndKeepsIdsUnique() throws IOException {
		MarketStore store = store();
		UUID seller = UUID.randomUUID();
		store.add(seller, "Ala", 10, item("minecraft:dirt"));
		MarketStore.Listing kept = store.add(seller, "Ala", 20, item("minecraft:stone"));

		MarketStore loaded = store();
		assertTrue(loaded.load().isEmpty());
		assertEquals(2, loaded.all().size());
		MarketStore.Listing found = loaded.find(kept.id()).orElseThrow();
		assertEquals(20, found.price());
		assertEquals("minecraft:stone", found.item().getAsJsonObject().get("id").getAsString());
		assertEquals(kept.id() + 1, loaded.add(seller, "Ala", 5, item("minecraft:dirt")).id());
	}

	@Test
	void brokenFileIsMovedAside() throws IOException {
		Files.writeString(dir.resolve("market.json"), "[[[", StandardCharsets.UTF_8);
		MarketStore store = store();
		assertTrue(store.load().isPresent());
		assertFalse(Files.exists(dir.resolve("market.json")));
	}
}
