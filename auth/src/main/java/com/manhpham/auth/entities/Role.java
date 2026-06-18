package com.manhpham.auth.entities;

/**
 * Coarse role granted to a user. Kept minimal for the walking skeleton;
 * authorization rules live downstream and read this from the JWT.
 */
public enum Role {
    USER,
    ADMIN
}
