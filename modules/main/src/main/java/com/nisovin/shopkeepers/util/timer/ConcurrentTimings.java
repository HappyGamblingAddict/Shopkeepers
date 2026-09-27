package com.nisovin.shopkeepers.util.timer;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A {@link Timings} implementation that can be updated by multiple threads concurrently.
 * <p>
 * Use this instead of the single-threaded {@link Timer} if the measured work is performed by tasks
 * that run concurrently, for example on multiple region threads on Folia. Each execution is measured
 * with a {@link Timer} that is local to the executing task, and then recorded here.
 */
public class ConcurrentTimings implements Timings {

	private final AtomicLong counter = new AtomicLong();
	private final AtomicLong totalTimeNanos = new AtomicLong();
	private final AtomicLong maxTimeNanos = new AtomicLong();

	/**
	 * Records a single execution that took the given duration.
	 * 
	 * @param durationNanos
	 *            the duration of the execution, in nanoseconds
	 */
	public void record(long durationNanos) {
		counter.incrementAndGet();
		totalTimeNanos.addAndGet(durationNanos);
		maxTimeNanos.accumulateAndGet(durationNanos, Math::max);
	}

	@Override
	public void reset() {
		counter.set(0L);
		totalTimeNanos.set(0L);
		maxTimeNanos.set(0L);
	}

	@Override
	public long getCounter() {
		return counter.get();
	}

	@Override
	public double getAverageTimeMillis() {
		long executions = counter.get();
		if (executions == 0L) return 0.0;
		return (double) totalTimeNanos.get() / (double) executions / 1_000_000.0;
	}

	@Override
	public double getMaxTimeMillis() {
		return (double) maxTimeNanos.get() / 1_000_000.0;
	}
}
