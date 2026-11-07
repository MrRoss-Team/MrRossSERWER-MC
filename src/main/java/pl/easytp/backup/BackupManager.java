package pl.easytp.backup;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.easytp.EasyTP;
import pl.easytp.Msg;
import pl.easytp.config.EasyTPConfig;
import pl.easytp.moderation.Durations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BackupManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(EasyTP.MOD_ID);

	private static final long INITIAL_DELAY_MS = 2 * 60_000L;
	private static final long RETRY_MS = 10 * 60_000L;
	private static final long WATCHDOG_MINUTES = 15;
	private static final int LIST_LIMIT = 10;

	public interface Feedback {
		Feedback NONE = new Feedback() {
			@Override
			public void info(Msg msg, Object... args) {
			}

			@Override
			public void ok(Msg msg, Object... args) {
			}

			@Override
			public void error(Msg msg, Object... args) {
			}
		};

		void info(Msg msg, Object... args);

		void ok(Msg msg, Object... args);

		void error(Msg msg, Object... args);
	}

	private final MinecraftServer server;
	private final long startedAt = System.currentTimeMillis();
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> daemon(runnable, "EasyTP-Backup-Scheduler"));
	private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> daemon(runnable, "EasyTP-Backup-Worker"));

	private volatile long anchor;
	private volatile boolean running;
	private volatile boolean stopping;

	private BackupManager(MinecraftServer server) {
		this.server = server;
	}

	private static Thread daemon(Runnable runnable, String name) {
		Thread thread = new Thread(runnable, name);
		thread.setDaemon(true);
		return thread;
	}

	public static BackupManager start(MinecraftServer server) {
		BackupManager manager = new BackupManager(server);
		Path dir = backupDir(EasyTP.config());
		BackupService.cleanupTemp(dir);
		manager.anchor = manager.newestBackupTime(dir);
		manager.scheduler.scheduleWithFixedDelay(manager::check, 30, 60, TimeUnit.SECONDS);
		return manager;
	}

	public void stop() {
		stopping = true;
		scheduler.shutdownNow();
		worker.shutdown();
		try {
			if (!worker.awaitTermination(30, TimeUnit.SECONDS)) {
				worker.shutdownNow();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void check() {
		try {
			if (stopping || running) {
				return;
			}
			EasyTPConfig config = EasyTP.config();
			long now = System.currentTimeMillis();
			if (config.backupEnabled && now >= startedAt + INITIAL_DELAY_MS && now >= anchor + config.backupIntervalMinutes * 60_000L) {
				post(() -> begin(false, Feedback.NONE));
			}
		} catch (Throwable t) {
			LOGGER.error("EasyTP: błąd zegara kopii zapasowych", t);
		}
	}

	private void post(Runnable task) {
		try {
			server.execute(task);
		} catch (RuntimeException e) {
			LOGGER.debug("EasyTP: nie udało się zlecić zadania serwerowi (zamyka się?)", e);
		}
	}

	private ScheduledFuture<?> schedule(Runnable task, long minutes) {
		try {
			return scheduler.schedule(task, minutes, TimeUnit.MINUTES);
		} catch (RejectedExecutionException e) {
			return null;
		}
	}

	public void begin(boolean manual, Feedback feedback) {
		if (stopping) {
			return;
		}
		if (running) {
			feedback.error(Msg.BACKUP_BUSY);
			return;
		}
		EasyTPConfig config = EasyTP.config();
		long now = System.currentTimeMillis();
		long intervalMs = config.backupIntervalMinutes * 60_000L;
		if (!manual && (!config.backupEnabled || now < startedAt + INITIAL_DELAY_MS || now < anchor + intervalMs)) {
			return;
		}

		Path worldDir = worldDir();
		String worldName = worldDir.getFileName().toString();
		Path dir = backupDir(config);
		anchor = now;

		if (dir.startsWith(worldDir)) {
			LOGGER.error("EasyTP: folder kopii zapasowych {} leży wewnątrz świata {}; zmień backupDir w config/easytp.json", dir, worldDir);
			feedback.error(Msg.BACKUP_BAD_DIR);
			return;
		}
		try {
			Files.createDirectories(dir);
			long worldBytes = BackupService.totalSize(worldDir);
			long usable = Files.getFileStore(dir).getUsableSpace();
			if (!BackupService.hasEnoughSpace(usable, worldBytes)) {
				LOGGER.warn("EasyTP: za mało miejsca na kopię zapasową w {} (wolne {}, świat {})", dir,
					BackupService.formatSize(usable), BackupService.formatSize(worldBytes));
				feedback.error(Msg.BACKUP_NO_SPACE, BackupService.formatSize(BackupService.requiredSpace(worldBytes)));
				return;
			}
		} catch (IOException e) {
			LOGGER.error("EasyTP: nie można przygotować folderu kopii zapasowych {}", dir, e);
			feedback.error(Msg.BACKUP_FAILED);
			retrySoon(intervalMs);
			return;
		}

		boolean autosaveWasOn = server.isAutoSave();
		try {
			if (autosaveWasOn) {
				server.setAutoSave(false);
			}
			if (!server.saveEverything(true, true, true)) {
				throw new IllegalStateException("zapis świata nie powiódł się");
			}
		} catch (RuntimeException e) {
			if (autosaveWasOn) {
				server.setAutoSave(true);
			}
			LOGGER.error("EasyTP: nie udało się zapisać świata przed kopią zapasową", e);
			feedback.error(Msg.BACKUP_FAILED);
			retrySoon(intervalMs);
			return;
		}

		try {
			List<Path> existing = BackupService.listBackups(dir, worldName);
			Path newest = existing.isEmpty() ? null : existing.get(0);
			if (!manual && !BackupService.needsBackup(worldDir, newest)) {
				if (autosaveWasOn) {
					server.setAutoSave(true);
				}
				LOGGER.debug("EasyTP: świat bez zmian od ostatniej kopii, pomijam");
				return;
			}
		} catch (IOException e) {
			LOGGER.warn("EasyTP: nie udało się sprawdzić, czy świat się zmienił; robię kopię na wszelki wypadek", e);
		}

		running = true;
		feedback.info(Msg.BACKUP_STARTED);
		Path target = BackupService.uniqueTarget(dir, worldName, LocalDateTime.now());
		int keep = config.backupKeep;
		AtomicBoolean savingRestored = new AtomicBoolean(false);
		Runnable restoreSaving = () -> {
			if (autosaveWasOn && savingRestored.compareAndSet(false, true)) {
				server.setAutoSave(true);
			}
		};
		ScheduledFuture<?> watchdog = schedule(() -> post(restoreSaving), WATCHDOG_MINUTES);

		worker.execute(() -> {
			BackupService.Result result = null;
			Exception failure = null;
			try {
				result = BackupService.zipWorld(worldDir, target, () -> stopping);
			} catch (Exception e) {
				failure = e;
			}
			post(restoreSaving);

			int removed = 0;
			if (failure == null) {
				try {
					removed = BackupService.prune(dir, worldName, keep).size();
				} catch (IOException e) {
					LOGGER.warn("EasyTP: nie udało się usunąć starych kopii zapasowych", e);
				}
			}
			BackupService.Result done = result;
			Exception error = failure;
			int removedCount = removed;
			post(() -> {
				if (watchdog != null) {
					watchdog.cancel(false);
				}
				finish(done, error, removedCount, intervalMs, feedback);
			});
		});
	}

	private void finish(BackupService.Result result, Exception failure, int removed, long intervalMs, Feedback feedback) {
		running = false;
		if (failure != null) {
			if (failure instanceof BackupService.CancelledException) {
				LOGGER.info("EasyTP: kopia zapasowa przerwana (serwer się zamyka)");
			} else {
				LOGGER.error("EasyTP: kopia zapasowa się nie udała", failure);
				feedback.error(Msg.BACKUP_FAILED);
				retrySoon(intervalMs);
			}
			return;
		}
		anchor = System.currentTimeMillis();
		String size = BackupService.formatSize(result.bytes());
		LOGGER.info("EasyTP: kopia zapasowa świata gotowa: {} ({}, {} plików, {} ms){}", result.file().getFileName(), size, result.files(),
			result.millis(), removed > 0 ? ", usunięto starych kopii: " + removed : "");
		String took = result.millis() < 1000 ? "<1 s" : Durations.format(result.millis());
		feedback.ok(Msg.BACKUP_DONE, result.file().getFileName().toString(), size, took);
	}

	private void retrySoon(long intervalMs) {
		anchor = System.currentTimeMillis() - Math.max(0, intervalMs - RETRY_MS);
	}

	public void list(Feedback feedback) {
		Path dir = backupDir(EasyTP.config());
		String worldName = worldDir().getFileName().toString();
		try {
			List<Path> backups = BackupService.listBackups(dir, worldName);
			if (backups.isEmpty()) {
				feedback.info(Msg.BACKUP_LIST_NONE, dir.toString());
				return;
			}
			feedback.info(Msg.BACKUP_LIST_HEADER, dir.toString(), backups.size());
			long now = System.currentTimeMillis();
			for (Path backup : backups.stream().limit(LIST_LIMIT).toList()) {
				long modified = Files.getLastModifiedTime(backup).toMillis();
				feedback.info(Msg.BACKUP_LIST_ENTRY, backup.getFileName().toString(), BackupService.formatSize(Files.size(backup)),
					Durations.format(Math.max(0, now - modified)));
			}
		} catch (IOException e) {
			LOGGER.error("EasyTP: nie udało się odczytać listy kopii zapasowych z {}", dir, e);
			feedback.error(Msg.BACKUP_FAILED);
		}
	}

	private Path worldDir() {
		return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
	}

	static Path backupDir(EasyTPConfig config) {
		Path dir = Path.of(config.backupDir);
		if (!dir.isAbsolute()) {
			dir = FabricLoader.getInstance().getGameDir().resolve(dir);
		}
		return dir.toAbsolutePath().normalize();
	}

	private long newestBackupTime(Path dir) {
		try {
			List<Path> existing = BackupService.listBackups(dir, worldDir().getFileName().toString());
			return existing.isEmpty() ? 0 : Files.getLastModifiedTime(existing.get(0)).toMillis();
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("EasyTP: nie udało się ustalić czasu ostatniej kopii zapasowej", e);
			return 0;
		}
	}
}
