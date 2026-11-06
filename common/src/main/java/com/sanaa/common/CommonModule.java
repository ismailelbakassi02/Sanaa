package com.sanaa.common;

/**
 * Marker class for the shared {@code common} library.
 *
 * <p>WHY: keeps the module non-empty so the jar packaging plugin produces a valid artifact
 * and gives other modules a stable entry point to depend on.
 */
public final class CommonModule {

    private CommonModule() {
    }
}
