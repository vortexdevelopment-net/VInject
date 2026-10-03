package net.vortexdevelopment.vinject.di.scan.fixtures.root;

import net.vortexdevelopment.vinject.annotation.component.Root;

@Root(
        packageName = "net.vortexdevelopment.vinject.di.scan.fixtures.root",
        includedPackages = "net.vortexdevelopment.vinject.di.scan.fixtures.included",
        createInstance = false
)
public class IncludedScannerRoot {
}
