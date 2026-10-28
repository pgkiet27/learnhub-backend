package com.learnhub.course.datasource;

import com.learnhub.course.datasource.ReplicaRoutingDataSource.Target;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReplicaRoutingDataSourceTest {

    @AfterEach
    void reset() {
        ReplicaContext.set(false);
        TransactionSynchronizationManager.setActualTransactionActive(false);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
    }

    @Test
    @DisplayName("Without @ReadFromReplica every query goes to the primary")
    void defaultsToPrimary() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

        assertThat(ReplicaRoutingDataSource.lookupKey()).isEqualTo(Target.PRIMARY);
    }

    @Test
    @DisplayName("@ReadFromReplica inside a read-only transaction uses the replica")
    void readOnlyTransactionUsesReplica() {
        ReplicaContext.set(true);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

        assertThat(ReplicaRoutingDataSource.lookupKey()).isEqualTo(Target.REPLICA);
    }

    @Test
    @DisplayName("@ReadFromReplica without a transaction uses the replica")
    void noTransactionUsesReplica() {
        ReplicaContext.set(true);

        assertThat(ReplicaRoutingDataSource.lookupKey()).isEqualTo(Target.REPLICA);
    }

    @Test
    @DisplayName("A read-write transaction stays on the primary even under @ReadFromReplica")
    void readWriteTransactionStaysOnPrimary() {
        ReplicaContext.set(true);
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertThat(ReplicaRoutingDataSource.lookupKey()).isEqualTo(Target.PRIMARY);
    }

    @Test
    @DisplayName("The aspect sets the flag only while the annotated method runs")
    void aspectScopesFlagToMethod() {
        Probe probe = proxied(new Probe());

        assertThat(probe.annotated()).isTrue();
        assertThat(probe.plain()).isFalse();
        assertThat(ReplicaContext.isActive()).isFalse();
    }

    @Test
    @DisplayName("The aspect clears the flag when the method throws")
    void aspectClearsFlagOnException() {
        Probe probe = proxied(new Probe());

        assertThatThrownBy(probe::failing).isInstanceOf(IllegalStateException.class);
        assertThat(ReplicaContext.isActive()).isFalse();
    }

    private static Probe proxied(Probe target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new ReadReplicaAspect());
        return factory.getProxy();
    }

    static class Probe {

        @ReadFromReplica
        public boolean annotated() {
            return ReplicaContext.isActive();
        }

        public boolean plain() {
            return ReplicaContext.isActive();
        }

        @ReadFromReplica
        public void failing() {
            throw new IllegalStateException("boom");
        }
    }
}
