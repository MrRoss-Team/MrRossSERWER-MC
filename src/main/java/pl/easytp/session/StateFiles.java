package pl.easytp.session;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import java.util.Optional;
import java.util.UUID;

public final class StateFiles {
	private static final Logger LOGGER = LoggerFactory.getLogger("easytp");
	private static final Gson GSON = new GsonBuilder().create();

	private final Path dir;

	public StateFiles(Path dir) {
		this.dir = dir;
	}

	private Path file(UUID id) {
		return dir.resolve(id + ".json");
	}

	public boolean exists(UUID id) {
		return Files.exists(file(id));
	}

	public void save(UUID id, JsonObject state) throws IOException {
		Files.createDirectories(dir);
		Path target = file(id);
		Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
		try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
			GSON.toJson(state, writer);
		}
		try {
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	public Optional<JsonObject> load(UUID id) {
		Path target = file(id);
		if (!Files.exists(target)) {
			return Optional.empty();
		}
		try (Reader reader = Files.newBufferedReader(target, StandardCharsets.UTF_8)) {
			return Optional.of(JsonParser.parseReader(reader).getAsJsonObject());
		} catch (IOException | JsonParseException | IllegalStateException e) {
			LOGGER.error("EasyTP: nie można odczytać stanu gracza z {}", target, e);
			return Optional.empty();
		}
	}

	public void delete(UUID id) {
		try {
			Files.deleteIfExists(file(id));
		} catch (IOException e) {
			LOGGER.error("EasyTP: nie udało się usunąć pliku {}", file(id), e);
		}
	}
}
