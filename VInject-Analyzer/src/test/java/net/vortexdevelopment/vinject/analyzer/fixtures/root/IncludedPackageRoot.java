package net.vortexdevelopment.vinject.analyzer.fixtures.root;

import net.vortexdevelopment.vinject.annotation.component.Root;

@Root(
        packageName = "net.vortexdevelopment.vinject.analyzer.fixtures.root",
        includedPackages = "net.vortexdevelopment.vinject.analyzer.fixtures.included",
        createInstance = false
)
public class IncludedPackageRoot {
}
