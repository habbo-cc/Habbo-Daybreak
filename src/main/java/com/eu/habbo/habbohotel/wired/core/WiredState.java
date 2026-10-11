package com.eu.habbo.habbohotel.wired.core;

import java.util.UUID;

/**
 * Tracks execution state for a wired stack run, providing loop safety and metadata.
 * <p>
 * Each wired stack execution gets its own WiredState instance that tracks:
 * <ul>
 *   <li>A unique run ID for debugging/tracing</li>
 *   <li>Step count to prevent infinite loops</li>
 *   <li>Maximum allowed steps before throwing {@link WiredLimitException}</li>
 * </ul>
 * </p>
 * 
 * <h3>Usage:</h3>
 * <pre>{@code
 * WiredState state = new WiredState(100); // max 100 steps
 * state.step(); // must call before each condition/effect
 * // ... execute condition/effect ...
 * }</pre>
 * 
 * @see WiredLimitException
 * @see WiredContext
 */
public final class WiredState {
    
    private final WiredSafetyBudget budget;
    private final WiredExecutionPath executionPath;
    private final int maxSteps;
    private int steps = 0;
    private long startTimeMs;
    private boolean aborted = false;
    private String abortReason;

    /**
     * Create a new wired state with the specified step limit.
     * @param maxSteps maximum number of steps allowed (triggers, conditions, effects)
     */
    public WiredState(int maxSteps) {
        this(maxSteps, WiredSafetyBudget.forSteps(maxSteps));
    }

    /**
     * Create a child stack state that consumes a shared root execution budget.
     */
    public WiredState(int maxSteps, WiredSafetyBudget budget) {
        this(maxSteps, budget, new WiredExecutionPath(budget));
    }

    private WiredState(int maxSteps, WiredSafetyBudget budget, WiredExecutionPath executionPath) {
        if (maxSteps <= 0) throw new IllegalArgumentException("Max steps must be positive");
        if (budget == null) throw new IllegalArgumentException("Budget cannot be null");
        if (executionPath == null) throw new IllegalArgumentException("Execution path cannot be null");
        this.budget = budget;
        this.executionPath = executionPath;
        this.maxSteps = maxSteps;
        this.startTimeMs = System.currentTimeMillis();
    }

    /**
     * Get the unique identifier for this execution run.
     * Useful for debugging and tracing wired execution across logs.
     * @return the run UUID
     */
    public UUID runId() {
        return this.budget.runId();
    }

    public WiredSafetyBudget budget() {
        return this.budget;
    }

    public WiredState fork() {
        return new WiredState(this.maxSteps, this.budget, this.executionPath.fork());
    }

    public WiredState fork(int childMaxSteps) {
        return new WiredState(childMaxSteps, this.budget, this.executionPath.fork());
    }

    public WiredSafetyBudget.PathLease enter(WiredSafetyBudget.PathKind kind, long stableId) {
        return this.executionPath.enter(kind, stableId);
    }

    /**
     * Get the current step count.
     * @return number of steps executed so far
     */
    public synchronized int steps() {
        return steps;
    }

    /**
     * Get the maximum allowed steps.
     * @return the step limit
     */
    public int maxSteps() {
        return maxSteps;
    }

    /**
     * Get the time when this execution started.
     * @return start time in milliseconds since epoch
     */
    public long startTimeMs() {
        return startTimeMs;
    }

    /**
     * Get the elapsed time since execution started.
     * @return elapsed time in milliseconds
     */
    public long elapsedMs() {
        return System.currentTimeMillis() - startTimeMs;
    }

    /**
     * Check if the execution has been aborted.
     * @return true if aborted
     */
    public synchronized boolean isAborted() {
        return aborted;
    }

    /**
     * Get the reason for abortion, if any.
     * @return the abort reason, or null if not aborted
     */
    public synchronized String abortReason() {
        return abortReason;
    }

    /**
     * Increment the step counter and check for limit violation.
     * Call this before each trigger match, condition evaluation, or effect execution.
     * 
     * @throws WiredLimitException if the step limit has been exceeded
     */
    public synchronized void step() {
        if (aborted) {
            throw new WiredLimitException("Wired execution was aborted: " + abortReason);
        }
        
        if (steps >= maxSteps) {
            throw new WiredLimitException(
                    "Wired execution exceeded max steps: " + maxSteps + 
                    " (runId: " + runId() + ")");
        }
        this.budget.step();
        steps++;
    }

    /**
     * Check if we can still execute more steps without throwing.
     * @return true if more steps are allowed
     */
    public synchronized boolean canStep() {
        return !aborted && steps < maxSteps && this.budget.canStep();
    }

    /**
     * Get remaining steps before hitting the limit.
     * @return number of remaining steps
     */
    public synchronized int remainingSteps() {
        return Math.min(Math.max(0, maxSteps - steps), this.budget.remainingSteps());
    }

    /**
     * Abort this execution with a reason.
     * Subsequent calls to {@link #step()} will throw.
     * @param reason the reason for aborting
     */
    public synchronized void abort(String reason) {
        this.aborted = true;
        this.abortReason = reason;
    }

    /**
     * Reset the step counter (use with caution).
     * This is mainly for testing purposes.
     */
    public synchronized void reset() {
        this.steps = 0;
        this.aborted = false;
        this.abortReason = null;
        this.startTimeMs = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return "WiredState{" +
                "runId=" + runId() +
                ", steps=" + steps + "/" + maxSteps +
                ", elapsed=" + elapsedMs() + "ms" +
                (aborted ? ", ABORTED: " + abortReason : "") +
                '}';
    }
}
