package com.learnhub.course.datasource;

/** Per-thread flag set by {@link ReadReplicaAspect} while a {@link ReadFromReplica} method runs. */
public final class ReplicaContext {

    private static final ThreadLocal<Boolean> READ_FROM_REPLICA = ThreadLocal.withInitial(() -> false);

    private ReplicaContext() {
    }

    public static boolean isActive() {
        return READ_FROM_REPLICA.get();
    }

    static void set(boolean active) {
        if (active) {
            READ_FROM_REPLICA.set(true);
        } else {
            READ_FROM_REPLICA.remove();
        }
    }
}
