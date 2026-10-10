package com.pawzaar.common.image;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * H5: the contract of {@link StorageCleanup} in isolation - immediate when there is no transaction,
 * deferred to commit/rollback when there is one. The end-to-end behaviour is proven by
 * {@code PetImageDeletionAfterCommitTest}; this just pins the helper's semantics.
 */
class StorageCleanupTest {

    @AfterEach
    void clearAnySynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void afterCommitRunsImmediatelyWhenNoTransactionIsActive() {
        AtomicInteger runs = new AtomicInteger();

        StorageCleanup.afterCommit(runs::incrementAndGet);

        assertEquals(1, runs.get(), "outside a transaction the action runs right away");
    }

    @Test
    void afterCommitWaitsForTheTransactionToCommit() {
        TransactionSynchronizationManager.initSynchronization();
        AtomicInteger runs = new AtomicInteger();

        StorageCleanup.afterCommit(runs::incrementAndGet);
        assertEquals(0, runs.get(), "must NOT run before the commit");

        synchronizations().forEach(TransactionSynchronization::afterCommit);
        assertEquals(1, runs.get());
    }

    @Test
    void afterRollbackRunsOnlyWhenTheTransactionDidNotCommit() {
        TransactionSynchronizationManager.initSynchronization();
        AtomicInteger runs = new AtomicInteger();

        StorageCleanup.afterRollback(runs::incrementAndGet);
        synchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        assertEquals(0, runs.get(), "a committed transaction must not trigger rollback cleanup");

        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.initSynchronization();
        StorageCleanup.afterRollback(runs::incrementAndGet);
        synchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertEquals(1, runs.get(), "a rolled-back transaction must trigger cleanup");
    }

    private static java.util.List<TransactionSynchronization> synchronizations() {
        return TransactionSynchronizationManager.getSynchronizations();
    }
}
