package com.nisovin.shopkeepers.paper;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.util.java.Validate;
import com.nisovin.shopkeepers.util.task.TaskHandle;
import com.nisovin.shopkeepers.util.task.TaskScheduler;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * A {@link TaskScheduler} that uses the regionized schedulers provided by Paper and Folia.
 * <p>
 * Global tasks run on the global tick thread, tasks tied to a location or entity run on the thread
 * that owns them, and async tasks run on a thread pool. This is the only correct way to access
 * entities and blocks on Folia, where each region is ticked by its own thread.
 * <p>
 * This class is only loaded on Folia, because it requires the Paper API.
 */
public class FoliaTaskScheduler implements TaskScheduler {

	private static final long TICKS_TO_MILLISECONDS = 50L;

	// The regionized schedulers do not expose the number of active async tasks, so we keep track of
	// the currently running async executions ourselves.
	private final AtomicInteger activeAsyncTasks = new AtomicInteger();

	@Override
	public @Nullable TaskHandle run(Plugin plugin, Runnable task) {
		return this.runGlobal(plugin, task, 0L, null);
	}

	@Override
	public @Nullable TaskHandle runDelayed(Plugin plugin, Runnable task, long delayTicks) {
		return this.runGlobal(plugin, task, delayTicks, null);
	}

	@Override
	public @Nullable TaskHandle runTimer(
			Plugin plugin,
			Runnable task,
			long delayTicks,
			long periodTicks
	) {
		return this.runGlobal(plugin, task, delayTicks, periodTicks);
	}

	@Override
	public @Nullable TaskHandle runAtLocation(Plugin plugin, Location location, Runnable task) {
		Validate.notNull(location, "location is null");
		return this.runAtRegion(plugin, location, task, 0L);
	}

	@Override
	public @Nullable TaskHandle runAtLocationDelayed(
			Plugin plugin,
			Location location,
			Runnable task,
			long delayTicks
	) {
		Validate.notNull(location, "location is null");
		return this.runAtRegion(plugin, location, task, delayTicks);
	}

	@Override
	public @Nullable TaskHandle runForEntity(Plugin plugin, Entity entity, Runnable task) {
		Validate.notNull(entity, "entity is null");
		return this.runAtEntity(plugin, entity, task, 0L);
	}

	@Override
	public @Nullable TaskHandle runForEntityDelayed(
			Plugin plugin,
			Entity entity,
			Runnable task,
			long delayTicks
	) {
		Validate.notNull(entity, "entity is null");
		return this.runAtEntity(plugin, entity, task, delayTicks);
	}

	@Override
	public @Nullable TaskHandle runAsync(Plugin plugin, Runnable task) {
		return this.runAsyncDelayed(plugin, task, 0L);
	}

	@Override
	public @Nullable TaskHandle runAsyncDelayed(Plugin plugin, Runnable task, long delayTicks) {
		validatePluginTask(plugin, task);
		// Tasks can only be registered while the plugin is enabled:
		if (!plugin.isEnabled()) return null;

		Runnable countedTask = () -> {
			try {
				task.run();
			} finally {
				activeAsyncTasks.decrementAndGet();
			}
		};

		try {
			activeAsyncTasks.incrementAndGet();
			ScheduledTask scheduledTask;
			if (delayTicks <= 0L) {
				scheduledTask = Bukkit.getAsyncScheduler().runNow(plugin, (ignored) -> countedTask.run());
			} else {
				// The async scheduler measures delays in real time, not in ticks:
				scheduledTask = Bukkit.getAsyncScheduler().runDelayed(
						plugin,
						(ignored) -> countedTask.run(),
						TICKS_TO_MILLISECONDS * delayTicks,
						TimeUnit.MILLISECONDS
				);
			}
			return new FoliaTaskHandle(scheduledTask);
		} catch (IllegalPluginAccessException e) {
			// Couldn't register task: The plugin got disabled just now.
			activeAsyncTasks.decrementAndGet();
			return null;
		}
	}

	@Override
	public boolean isGlobalThread() {
		return Bukkit.isGlobalTickThread();
	}

	@Override
	public int getActiveAsyncTasks(Plugin plugin) {
		Validate.notNull(plugin, "plugin is null");
		return activeAsyncTasks.get();
	}

	private static void validatePluginTask(Plugin plugin, Runnable task) {
		Validate.notNull(plugin, "plugin is null");
		Validate.notNull(task, "task is null");
	}

	// A null period indicates a task that only runs once.
	private @Nullable TaskHandle runGlobal(
			Plugin plugin,
			Runnable task,
			long delayTicks,
			@Nullable Long periodTicks
	) {
		validatePluginTask(plugin, task);
		if (!plugin.isEnabled()) return null;

		try {
			GlobalRegionScheduler scheduler = Bukkit.getGlobalRegionScheduler();
			ScheduledTask scheduledTask;
			if (periodTicks == null) {
				scheduledTask = scheduler.runDelayed(plugin, (ignored) -> task.run(), delayTicks);
			} else {
				scheduledTask = scheduler.runAtFixedRate(
						plugin,
						(ignored) -> task.run(),
						delayTicks,
						periodTicks
				);
			}
			return new FoliaTaskHandle(scheduledTask);
		} catch (IllegalPluginAccessException e) {
			// Couldn't register task: The plugin got disabled just now.
			return null;
		}
	}

	private @Nullable TaskHandle runAtRegion(
			Plugin plugin,
			Location location,
			Runnable task,
			long delayTicks
	) {
		validatePluginTask(plugin, task);
		if (!plugin.isEnabled()) return null;

		org.bukkit.World world = location.getWorld();
		if (world == null) return null; // The world is not loaded

		try {
			RegionScheduler scheduler = Bukkit.getRegionScheduler();
			ScheduledTask scheduledTask = scheduler.runDelayed(
					plugin,
					world,
					location.getBlockX() >> 4,
					location.getBlockZ() >> 4,
					(ignored) -> task.run(),
					delayTicks
			);
			return new FoliaTaskHandle(scheduledTask);
		} catch (IllegalPluginAccessException e) {
			// Couldn't register task: The plugin got disabled just now.
			return null;
		}
	}

	private @Nullable TaskHandle runAtEntity(
			Plugin plugin,
			Entity entity,
			Runnable task,
			long delayTicks
	) {
		validatePluginTask(plugin, task);
		if (!plugin.isEnabled()) return null;

		try {
			EntityScheduler scheduler = entity.getScheduler();
			// The retired callback is invoked if the entity is removed before the task runs. In that
			// case, the task must not be run at all.
			// Note: The scheduling call returns null if the entity has already been retired.
			ScheduledTask scheduledTask = scheduler.runDelayed(
					plugin,
					(ignored) -> task.run(),
					() -> {},
					delayTicks
			);
			if (scheduledTask == null) return null; // The entity is no longer valid
			return new FoliaTaskHandle(scheduledTask);
		} catch (IllegalPluginAccessException e) {
			// Couldn't register task: The plugin got disabled just now.
			return null;
		}
	}

	private static final class FoliaTaskHandle implements TaskHandle {

		private final ScheduledTask task;

		FoliaTaskHandle(ScheduledTask task) {
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
