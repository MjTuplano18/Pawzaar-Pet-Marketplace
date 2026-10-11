package com.pawzaar.adoption;

/** Lifecycle states of an adoption request. */
public enum AdoptionStatus {
    /** Submitted and awaiting a decision from the pet owner. */
    PENDING,
    /** The pet owner has accepted the request. */
    APPROVED,
    /** The pet owner has declined the request. */
    REJECTED,
    /** The requester cancelled before a decision was made. */
    WITHDRAWN
}
