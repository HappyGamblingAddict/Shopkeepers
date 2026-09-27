package com.nisovin.shopkeepers.util.task;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.util.bukkit.ServerUtils;
import com.nisovin.shopkeepers.util.java.Validate;
import com.nisovin.shopkeepers.util.logging.Log;

/**
 * Provides access to the {@link TaskScheduler} implementation that matches the current server
 * platform.
 * <p>
 * The scheduler is initialized during plugin startup via {@link #init()}. Until then, and if the
 * platform specific implementation cannot be loaded, the {@link BukkitTaskScheduler} is used.
 */
public final class TaskSchedulers {

	private static final String FOLIA_SCHEDULER_CLASS_NAME
			= "com.nisovin.shopkeepers.paper.FoliaTaskScheduler";

	private static TaskScheduler scheduler = new BukkitTaskScheduler();

	/**
	 * Gets the {@link TaskScheduler} for the current server platform.
	 * 
	 * @return the task scheduler, not <code>null</code>
	 */
	public static TaskScheduler get() {
		return scheduler;
	}

	/**
	 * Initializes the {@link TaskScheduler} for the current server platform.
	 * <p>
	 * On Folia, the regionized schedulers are used. On all other server platforms, the Bukkit
	 * scheduler is used.
	 * 
	 * @return <code>true</code> on success
	 */
	public static boolean init() {
		if (!ServerUtils.isFolia()) {
			// The Bukkit scheduler is already set up:
			return true;
		}

		TaskScheduler foliaScheduler = loadFoliaTaskScheduler();
		if (foliaScheduler == null) {
			// Fall back to the Bukkit scheduler. This is not safe on Folia, but it allows the caller
			// to detect the unsupported platform and shut down gracefully.
			Log.severe("Could not load the Folia task scheduler! Folia is not supported yet.");
			return false;
		}

		scheduler = foliaScheduler;
		Log.info("Folia task scheduler loaded.");
		return true;
	}

	private static @Nullable TaskScheduler loadFoliaTaskScheduler() {
		try {
			Class<?> clazz = Class.forName(FOLIA_SCHEDULER_CLASS_NAME);
			return (TaskScheduler) clazz.getConstructor().newInstance();
		} catch (Exception e) {
			Log.warning("Failed to load the Folia task scheduler!", e);
			return null;
		}
	}

	/**
	 * Resets the task scheduler to the default implementation. Only intended for tests.
	 */
	public static void reset() {
		scheduler = new BukkitTaskScheduler();
	}

	/**
	 * Sets the task scheduler explicitly.
	 * 
	 * @param newScheduler
	 *            the new scheduler, not <code>null</code>
	 */
	public static void set(@Nullable TaskScheduler newScheduler) {
		scheduler = Validate.notNull(newScheduler, "newScheduler is null");
	}

	private TaskSchedulers() {
	}
}
