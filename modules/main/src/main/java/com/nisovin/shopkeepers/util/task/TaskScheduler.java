package com.nisovin.shopkeepers.util.task;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Schedules tasks, abstracting away the differences between the single main thread provided by the
 * {@link org.bukkit.scheduler.BukkitScheduler Bukkit scheduler} and the multiple region and entity
 * threads provided by Folia.
 * <p>
 * Tasks that are not tied to a specific location or entity are scheduled globally. Whether such a
 * global task may access entities or blocks is not implied by this interface: On Folia, only the
 * region thread owning the accessed location or entity may do so. Use
 * {@link #runAtLocation(Plugin, Location, Runnable)} and
 * {@link #runForEntity(Plugin, Entity, Runnable)} for any work that touches the world.
 * <p>
 * All scheduling methods return <code>null</code> if the task could not be registered, which is the
 * case if the plugin got disabled in the meantime.
 */
public interface TaskScheduler {

	// GLOBAL TASKS

	/**
	 * Schedules the given task to be run on the global thread during the next tick.
	 * 
	 * @param plugin
	 *            the plugin, not <code>null</code>
	 * @param task
	 *            the task, not <code>null</code>
	 * @return the task handle, or <code>null</code> if the task could not be registered
	 */
	public @Nullable TaskHandle run(Plugin plugin, Runnable task);

	/**
	 * Schedules the given task to be run on the global thread after the given delay.
	 * 
	 * @param plugin
	 *            the plugin, not <code>null</code>
	 * @param task
	 *            the task, not <code>null</code>
	 * @param delayTicks
	 *            the delay in ticks
	 * @return the task handle, or <code>null</code> if the task could not be registered
	 */
	public @Nullable TaskHandle runDelayed(Plugin plugin, Runnable task, long delayTicks);

	/**
	 * Schedules the given task to be run repeatedly on the global thread.
	 * 
	 * @param plugin
	 *            the plugin, not <code>null</code>
	 * @param task
	 *            the task, not <code>null</code>
	 * @param delayTicks
	 *            the delay until the first execution, in ticks
	 * @param periodTicks
	 *            the period between executions, in ticks
	 * @return the task handle, or <code>null</code> if the task could not be registered
	 */
	public @Nullable TaskHandle runTimer(
			Plugin plugin,
			Runnable task,
			long delayTicks,
			long periodTicks
	);

	// REGION TASKS

	/**
	 * Schedules the given task to be run on the thread that owns the given location.
	 * 
	 * @param plugin
	 *            the plugin, not <code>null</code>
	 * @param location
	 *            the location, not <code>null</code>
	 * @param task
	 *            the task, not <code>null</code>
	 * @return the task handle, or <code>null</code> if the task could not be registered
	 */
	public @Nullable TaskHandle runAtLocation(Plugin plugin, Location location, Runnable task);

	/**
	 * Schedules the given task to be run on the thread that owns the given location, after the given
	 * delay.
	 * 
	 * @param plugin
	 *            the plugin, not <code>null</code>
	 * @param location
	 *            the location, not <code>null</code>
	 * @param task
	 *            the task, not <code>null</code>
	 * @param delayTicks
	 *            the delay in ticks
	 * @return the task handle, or <code>null</code> if the task could not be registered
	 */
	public @Nullable TaskHandle runAtLocationDelayed(
			Plugin plugin,
			Location location,
			Runnable task,
			long delayTicks
	);

	// ENTITY TASKS

	/**
	 * Schedules the given task to be run on the thread that owns the given entity.
	 * 
	 * @param plugin
	 *            the plugin, not <code>null</code>
	 * @param entity
	 *            the entity, not <code>null</code>
	 * @param task
	 *            the task, not <code>null</code>
	 * @return the task handle, or <code>null</code> if the task could not be registered
	 */
	public @Nullable TaskHandle runForEntity(Plugin plugin, Entity entity, Runnable task);

	/**
	 * Schedules the given task to be run on the thread that owns the given entity, after the given
	 * delay.
	 * 
	 * @param plugin
	 *            the plugin, not <code>null</code>
	 * @param entity
	 *            the entity, not <code>null</code>
	 * @param task
	 *            the task, not <code>null</code>
	 * @param delayTicks
	 *            the delay in ticks
	 * @return the task handle, or <code>null</code> if the task could not be registered
	 */
	public @Nullable TaskHandle runForEntityDelayed(
			Plugin plugin,
			Entity entity,
			Runnable task,
			long delayTicks
	);

	// ASYNC TASKS

	/**
	 * Schedules the given task to be run asynchronously as soon as possible.
	 * 
	 * @param plugin
	 *            the plugin, not <code>null</code>
	 * @param task
	 *            the task, not <code>null</code>
	 * @return the task handle, or <code>null</code> if the task could not be registered
	 */
	public @Nullable TaskHandle runAsync(Plugin plugin, Runnable task);

	/**
	 * Schedules the given task to be run asynchronously after the given delay.
	 * 
	 * @param plugin
	 *            the plugin, not <code>null</code>
	 * @param task
	 *            the task, not <code>null</code>
	 * @param delayTicks
	 *            the delay in ticks
	 * @return the task handle, or <code>null</code> if the task could not be registered
	 */
	public @Nullable TaskHandle runAsyncDelayed(Plugin plugin, Runnable task, long delayTicks);

	// THREAD CHECKS

	/**
	 * Checks whether the current thread is the thread that runs global tasks.
	 * <p>
	 * A global task may be run immediately instead of being scheduled if this returns
	 * <code>true</code>. Note that this does not imply that the current thread may access entities or
	 * blocks, since the global thread does not own any region.
	 * 
	 * @return <code>true</code> if the current thread runs global tasks
	 */
	public boolean isGlobalThread();

	/**
	 * Gets the number of async tasks that are currently active for the given plugin.
	 * 
	 * @param plugin
	 *            the plugin, not <code>null</code>
	 * @return the number of active async tasks
	 */
	public int getActiveAsyncTasks(Plugin plugin);

	/**
	 * Cancels all tasks that have been scheduled by the given plugin.
	 * 
	 * @param plugin
	 *            the plugin, not <code>null</code>
	 */
	public void cancelAllTasks(Plugin plugin);
}
