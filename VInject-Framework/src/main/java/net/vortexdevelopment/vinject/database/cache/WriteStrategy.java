package net.vortexdevelopment.vinject.database.cache;

/**
 * Enum defining write strategies for cached entities.
 */
public enum WriteStrategy {
    /**
     * Internal sentinel value for inherited configurations.
     */
    UNDEFINED,

    /**
     * Write-through strategy: updates are immediately persisted to the database.
     * Cache stays consistent with the database at all times.
     * Safer but potentially slower for write-heavy workloads.
     */
    WRITE_THROUGH,
    
    /**
     * Write-back (write-behind) strategy: updates are marked dirty and flushed periodically.
     * Faster writes but riskier - unflushed changes may be lost on application crash.
     * Requires periodic flush tasks or manual flush calls.
     */
    WRITE_BACK
}
