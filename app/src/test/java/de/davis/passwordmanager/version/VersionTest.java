package de.davis.passwordmanager.version;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class VersionTest {

    private Version version;

    @Before
    public void setUp(){
        version = Version.fromVersionTag("v1.2.3-rc05");
    }

    @Test
    public void testVersionCodeAssembling(){
        assertEquals(10020368, version.getVersionCode());
    }

    @Test
    public void testVersionTagAssembling(){
        assertEquals("v1.2.3-rc05", version.getVersionTag());
    }

    @Test
    public void testVersionComponents(){
        assertEquals(1, version.getMajor());
        assertEquals(2, version.getMinor());
        assertEquals(3, version.getPatch());
        assertEquals(5, version.getBuild());
        assertEquals(5, version.getBuild());


        assertEquals(Version.CHANNEL_RC, version.getChannel());
        assertEquals(Version.CHANNEL_RC, Version.getChannelByVersionName(version.getVersionTag()));
    }
}
