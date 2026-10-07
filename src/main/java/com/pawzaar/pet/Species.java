package com.pawzaar.pet;

/**
 * Categories a pet can have. The database stores the constant NAME ("DOG", not 0)
 * because the entity uses @Enumerated(EnumType.STRING).
 */
public enum Species {
    DOG, CAT, OTHER
}
