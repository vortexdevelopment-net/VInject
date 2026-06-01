package net.vortexdevelopment.vinject.config.yaml;

/**
 * YAML block chomping indicator ({@code -} strip, {@code +} keep, default clip one trailing newline).
 */
enum BlockScalarChomping {
    STRIP,
    CLIP,
    KEEP
}
