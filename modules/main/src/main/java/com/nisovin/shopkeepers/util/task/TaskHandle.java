package com.nisovin.shopkeepers.util.task;

/**
 * A handle to a scheduled task, which allows the task to be cancelled.
 * <p>
 * This abstracts away the server specific task types, which differ between the tasks provided by
 * the {@link org.bukkit.scheduler.BukkitScheduler Bukkit scheduler} and the regionized schedulers
 * provided by Paper and Folia.
 */
public interface TaskHandle {

	/**
	 * Cancels the task.
	 * <p>
	 * The task may already have finished or have been cancelled before. Cancelling an already
	 * finished task has no effect.
	 */
	public void cancel();

	/**
	 * Checks whether this task has been cancelled.
	 * 
	 * @return <code>true</code> if the task was cancelled
	 */
	public boolean isCancelled();
}
