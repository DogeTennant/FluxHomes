package com.dogetennant.fluxhomes.models;

public enum SetHomeResult {
    SUCCESS,
    LIMIT_REACHED,
    WORLD_BLOCKED,
    /** Longer than {@link com.dogetennant.fluxhomes.managers.HomeManager#MAX_NAME_LENGTH}. */
    NAME_TOO_LONG
}
