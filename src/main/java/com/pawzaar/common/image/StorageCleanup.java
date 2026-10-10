package com.pawzaar.common.image;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Defers the irreversible storage operations that must follow a database change (H5).
 *
 * <p>The problem it solves: a service deletes a row and its file inside one {@code @Transactional}
 * method. Removing the file immediately is a side effect the database cannot roll back - if the
 * transaction later rolls back (a constraint on flush, a commit-time failure, a caller catching and
 * continuing), the row survives but its bytes are gone, and the API serves a broken image. The fix
 * is to keep the database as the source of truth: commit first, then delete the file.
 *
 * <p>Outside a transaction (or when no synchronization is active) the action runs immediately, which
 * is what a plain unit test - or a non-transactional caller - expects.
 */
public final class StorageCleanup {

    private StorageCleanup() {
    }

    /**
     * Runs {@code action} after the current transaction <b>commits</b>; immediately if there is no
     * active transaction. Use it for deletions that must not happen if the transaction rolls back.
     */
    public static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    /**
     * Runs {@code action} only if the current transaction does <b>not</b> commit (i.e. it rolls back),
     * and does nothing when there is no active transaction. Use it to clean up a file that a failing
     * transaction should never have left behind.
     */
    public static void afterRollback(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        action.run();
                    }
                }
            });
        }
    }
}
