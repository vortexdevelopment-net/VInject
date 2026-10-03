package net.vortexdevelopment.plugin.vinject;

import net.vortexdevelopment.plugin.vinject.project.ProjectMode;
import net.vortexdevelopment.plugin.vinject.version.MavenVersionResolver;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class PluginTest {

    @Test
    public void testProjectModes() {
        assertEquals(2, ProjectMode.values().length);
        assertNotNull(ProjectMode.valueOf("VORTEX"));
        assertNotNull(ProjectMode.valueOf("SONGODA"));
    }

    @Test
    public void testMavenVersionResolverSongodaCore() {
        MavenVersionResolver resolver = MavenVersionResolver.getInstance();
        String version = resolver.resolveVersion("com.songoda", "SongodaCore");
        assertNotNull(version);
        assertFalse(version.isEmpty());
    }

    @Test
    public void testMavenVersionResolverVortexCore() {
        MavenVersionResolver resolver = MavenVersionResolver.getInstance();
        String version = resolver.resolveVersion("net.vortexdevelopment", "VortexCore");
        assertNotNull(version);
        assertFalse(version.isEmpty());
    }
}
