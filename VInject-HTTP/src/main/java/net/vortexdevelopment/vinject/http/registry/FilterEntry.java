package net.vortexdevelopment.vinject.http.registry;

import jakarta.servlet.Filter;

/**
 * Represents a registered filter with its order and name.
 */
public record FilterEntry(Filter filter, int order, String name) {}
