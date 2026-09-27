package com.nisovin.shopkeepers.util.task;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitWorker;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.util.java.Validate;
import com.nisovin.shopkeepers.util.task.TaskHandle;

/**
 * A {@link TaskScheduler} that uses the {@link org.bukkit.scheduler.BukkitScheduler Bukkit
 * scheduler}, which runs all tasks on the server's single main thread.
 * <p>
 * Since there is only one thread, tasks that are tied to a specific location or entity are treated
 * the same as global tasks.
 */
public class BukkitTaskScheduler implements TaskScheduler {

	@Override
	public @Nullable TaskHandle run(Plugin plugin, Runnable task) {
		return this.schedule(plugin, task, 0L, null);
	}

	@Override
	public @Nullable TaskHandle runDelayed(Plugin plugin, Runnable task, long delayTicks) {
		return this.schedule(plugin, task, delayTicks, null);
	}

	@Override
	public @Nullable TaskHandle runTimer(
			Plugin plugin,
			Runnable task,
			long delayTicks,
			long periodTicks
	) {
		return this.schedule(plugin, task, delayTicks, periodTicks);
	}

	@Override
	public @Nullable TaskHandle runAtLocation(Plugin plugin, Location location, Runnable task) {
		Validate.notNull(location, "location is null");
		// There is only one thread, so the location does not have to be taken into account:
		return this.run(plugin, task);
	}

	@Override
	public @Nullable TaskHandle runAtLocationDelayed(
			Plugin plugin,
			Location location,
			Runnable task,
			long delayTicks
	) {
		Validate.notNull(location, "location is null");
		return this.runDelayed(plugin, task, delayTicks);
	}

	@Override
	public @Nullable TaskHandle runForEntity(Plugin plugin, Entity entity, Runnable task) {
		Validate.notNull(entity, "entity is null");
		return this.run(plugin, task);
	}

	@Override
	public @Nullable TaskHandle runForEntityDelayed(
			Plugin plugin,
			Entity entity,
			Runnable task,
			long delayTicks
	) {
		Validate.notNull(entity, "entity is null");
		return this.runDelayed(plugin, task, delayTicks);
	}

	@Override
	public @Nullable TaskHandle runAsync(Plugin plugin, Runnable task) {
		return this.scheduleAsync(plugin, task, 0L);
	}

	@Override
	public @Nullable TaskHandle runAsyncDelayed(Plugin plugin, Runnable task, long delayTicks) {
		return this.scheduleAsync(plugin, task, delayTicks);
	}

	@Override
	public boolean isGlobalThread() {
		return Bukkit.isPrimaryThread();
	}

	@Override
	public int getActiveAsyncTasks(Plugin plugin) {
		Validate.notNull(plugin, "plugin is null");
		int workers = 0;
		for (BukkitWorker worker : Bukkit.getScheduler().getActiveWorkers()) {
			if (worker.getOwner().equals(plugin)) {
				workers++;
			}
		}
		return workers;
	}

	@Override
	public void cancelAllTasks(Plugin plugin) {
		Validate.notNull(plugin, "plugin is null");
		try {
			Bukkit.getScheduler().cancelTasks(plugin);
		} catch (UnsupportedOperationException e) {
			// The Bukkit scheduler is not available (on Folia). This implementation can still be
			// active if the platform specific implementation could not be loaded. Cancelling tasks
			// is best effort during plugin disable, so we ignore this.
		}
	}

	private static void validatePluginTask(Plugin plugin, Runnable task) {
		Validate.notNull(plugin, "plugin is null");
		Validate.notNull(task, "task is null");
	}

	// A null period indicates a task that only runs once.
	private @Nullable TaskHandle schedule(
			Plugin plugin,
			Runnable task,
			long delayTicks,
			@Nullable Long periodTicks
	) {
		validatePluginTask(plugin, task);
		// Tasks can only be registered while the plugin is enabled:
		if (!plugin.isEnabled()) return null;

		try {
			BukkitTask bukkitTask;
			if (periodTicks == null) {
				bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
			} else {
				bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
			}
			return new BukkitTaskHandle(bukkitTask);
		} catch (IllegalPluginAccessException e) {
			// Couldn't register task: The plugin got disabled just now.
			return null;
		}
	}

	private @Nullable TaskHandle scheduleAsync(Plugin plugin, Runnable task, long delayTicks) {
		validatePluginTask(plugin, task);
		if (!plugin.isEnabled()) return null;

		try {
			BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLaterAsynchronously(
					plugin,
					task,
					delayTicks
			);
			return new BukkitTaskHandle(bukkitTask);
		} catch (IllegalPluginAccessException e) {
			// Couldn't register task: The plugin got disabled just now.
			return null;
		}
	}

	private static final class BukkitTaskHandle implements TaskHandle {

		private final BukkitTask task;

		BukkitTaskHandle(BukkitTask task) {
			this.task = task;
		}

		@Override
		public void cancel() {
			task.cancel();
		}

		@Override
		public boolean isCancelled() {
			return task.isCancelled();
		}
	}
}
