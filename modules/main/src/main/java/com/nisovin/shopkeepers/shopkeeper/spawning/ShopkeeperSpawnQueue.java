package com.nisovin.shopkeepers.shopkeeper.spawning;

import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import com.nisovin.shopkeepers.shopkeeper.AbstractShopkeeper;
import com.nisovin.shopkeepers.shopkeeper.spawning.ShopkeeperSpawnState.State;
import com.nisovin.shopkeepers.shopobjects.AbstractShopObject;
import com.nisovin.shopkeepers.util.java.Validate;
import com.nisovin.shopkeepers.util.task.TaskSchedulers;
import com.nisovin.shopkeepers.util.taskqueue.TaskQueue;

/**
 * A queue for load balancing the spawning of shopkeepers.
 * <p>
 * Spawning shopkeepers can be relatively costly performance-wise. In order to avoid performance
 * drops when chunks with lots of shopkeepers are activated, we use this queue to distribute the
 * spawning of shopkeepers over several ticks.
 * <p>
 * Shopkeepers may already be ticked while they are still pending to be spawned. Shop objects can
 * use {@link AbstractShopObject#isSpawningScheduled()} to check if they are currently still pending
 * to be spawned.
 */
public class ShopkeeperSpawnQueue extends TaskQueue<AbstractShopkeeper> {

	// With this configuration we can spawn around 40 shopkeepers per second.
	// A more frequently running task has a higher general overhead.
	private static final int SPAWN_TASK_PERIOD_TICKS = 3;
	// On my test setup, and without any GC taking place, the spawning of a shopkeeper seems to take
	// between 0.05-0.25ms, with an average of around 0.1ms.
	private static final int SPAWNS_PER_EXECUTION = 6;

	private final Plugin plugin;
	private final Consumer<? super AbstractShopkeeper> spawner;

	ShopkeeperSpawnQueue(Plugin plugin, Consumer<? super AbstractShopkeeper> spawner) {
		super(plugin, SPAWN_TASK_PERIOD_TICKS, SPAWNS_PER_EXECUTION);
		Validate.notNull(spawner, "spawner is null");
		this.plugin = plugin;
		this.spawner = spawner;
	}

	private static class SpawnerTask implements Runnable {

		private final Runnable parentTask;

		SpawnerTask(Runnable parentTask) {
			assert parentTask != null;
			this.parentTask = parentTask;
		}

		@Override
		public void run() {
			parentTask.run();
		}
	}

	private void setQueued(AbstractShopkeeper shopkeeper) {
		assert shopkeeper != null;
		ShopkeeperSpawnState spawnState = shopkeeper.getComponents().getOrAdd(ShopkeeperSpawnState.class);
		assert !spawnState.isSpawningScheduled();
		spawnState.setState(State.QUEUED);
	}

	private void resetQueued(AbstractShopkeeper shopkeeper) {
		assert shopkeeper != null;
		ShopkeeperSpawnState spawnState = shopkeeper.getComponents().getOrAdd(ShopkeeperSpawnState.class);
		// If this assertion throws: Make sure that the shopkeeper is getting removed from the queue
		// when the spawn state changes in the meantime, e.g. by calling
		// ShopkeeperSpawner#updateSpawnState instead of setting the spawn state directly.
		assert spawnState.getState() == State.QUEUED : "spawnState != QUEUED";
		spawnState.setState(State.DESPAWNED);
	}

	@Override
	protected void onAdded(AbstractShopkeeper shopkeeper) {
		super.onAdded(shopkeeper);
		// Mark the shopkeeper as 'queued':
		this.setQueued(shopkeeper);
	}

	@Override
	protected void onRemoval(AbstractShopkeeper shopkeeper) {
		super.onRemoval(shopkeeper);
		// Reset the shopkeeper's 'queued' state:
		this.resetQueued(shopkeeper);
	}

	@Override
	protected Runnable createTask() {
		return new SpawnerTask(super.createTask());
	}

	@Override
	protected void process(AbstractShopkeeper shopkeeper) {
		// Reset the shopkeeper's 'queued' state:
		this.resetQueued(shopkeeper);

		Location location = shopkeeper.getLocation();
		if (location == null) {
			// The shopkeeper has no valid location (anymore). It is likely getting removed:
			return;
		}

		// Spawn the shopkeeper on the thread that owns its location:
		// On Folia, spawning involves accessing the world (e.g. to find the ground below the
		// spawn location), which is only allowed on the thread that owns the region.
		// Note: The work unit has already been removed from the queue at this point, so we do not
		// have to keep track of whether the deferred task is still pending.
		TaskSchedulers.get().runAtLocation(plugin, location, () -> spawner.accept(shopkeeper));
	}
}
