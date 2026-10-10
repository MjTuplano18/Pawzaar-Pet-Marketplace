package com.pawzaar.common.limits;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Per-account guard rails for the marketplace (H6): how many listings one seller may keep, and how
 * many images one listing may hold.
 *
 * <p>These exist to stop a single account from filling the database and the image bucket - an abuse
 * and cost-control measure, not a product tier. Both are 12-factor config (override with the env
 * vars noted in {@code application.yaml}) so they can be tuned without a rebuild.
 */
@ConfigurationProperties(prefix = "pawzaar.limits")
public class ListingLimitsProperties {

    /**
     * Maximum number of non-deleted listings one seller may have at once. Soft-deleted (HIDDEN)
     * listings do not count, so deleting a listing frees the slot.
     */
    private int maxListingsPerUser = 20;

    /** Maximum number of images attached to a single listing. */
    private int maxImagesPerListing = 10;

    public int getMaxListingsPerUser() {
        return maxListingsPerUser;
    }

    public void setMaxListingsPerUser(int maxListingsPerUser) {
        this.maxListingsPerUser = maxListingsPerUser;
    }

    public int getMaxImagesPerListing() {
        return maxImagesPerListing;
    }

    public void setMaxImagesPerListing(int maxImagesPerListing) {
        this.maxImagesPerListing = maxImagesPerListing;
    }
}
