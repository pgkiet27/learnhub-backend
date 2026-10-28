package com.learnhub.course.datasource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class ReplicaRoutingDataSource extends AbstractRoutingDataSource {

    public enum Target { PRIMARY, REPLICA }

    @Override
    protected Object determineCurrentLookupKey() {
        return lookupKey();
    }

    // A read-write transaction always stays on the primary, even inside a @ReadFromReplica method
    static Target lookupKey() {
        boolean readOnlyOrNoTransaction = !TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        return ReplicaContext.isActive() && readOnlyOrNoTransaction ? Target.REPLICA : Target.PRIMARY;
    }
}
