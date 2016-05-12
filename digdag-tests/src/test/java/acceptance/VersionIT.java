package acceptance;

import io.digdag.cli.Context;
import io.digdag.core.Version;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;

import static acceptance.TestUtils.main;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

public class VersionIT
{
    private static final Version REMOTE_VERSION = Version.of("4.5.6");

    private static final Context SERVER_CONTEXT = Context.builder()
            .version(REMOTE_VERSION)
            .build();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Rule
    public TemporaryDigdagServer server = TemporaryDigdagServer.of(SERVER_CONTEXT);

    private Path config;

    @Before
    public void setUp()
            throws Exception
    {
        config = Files.createFile(folder.getRoot().toPath().resolve("config"));
    }

    @Test
    public void testVersionHeader()
            throws Exception
    {
        String versionString = "3.14.15";
        Context context = Context.builder()
                .version(Version.of(versionString))
                .build();
        CommandStatus status = main(context, "-c", config.toString());
        assertThat(status.errUtf8(), containsString("Digdag v" + versionString));
    }

    @Test
    public void testVersion()
            throws Exception
    {
        String localVersionString = REMOTE_VERSION + "-NOT";
        Context context = Context.builder()
                .version(Version.of(localVersionString))
                .build();
        CommandStatus status = main(context, "version", "-e", server.endpoint(), "-c", config.toString());
        assertThat(status.outUtf8(), containsString("Client version: " + localVersionString));
        assertThat(status.outUtf8(), containsString("Server version: " + REMOTE_VERSION));
    }

    @Test
    public void verifyVersionMismatchRejected()
            throws Exception
    {
        String localVersionString = REMOTE_VERSION + "-NOT";
        Context context = Context.builder()
                .version(Version.of(localVersionString))
                .build();
        CommandStatus status = main(context, "workflows", "-e", server.endpoint(), "-c", config.toString());
        assertThat(status.code(), is(not(0)));
        assertThat(status.errUtf8(), containsString("Client: " + localVersionString));
        assertThat(status.errUtf8(), containsString("Server: " + REMOTE_VERSION));
        assertThat(status.errUtf8(), containsString("Please run: digdag selfupdate"));
        assertThat(status.errUtf8(), containsString(
                "Before pushing workflows to the server, please run them locally to " +
                        "verify that they are compatible with the new version of digdag."));
    }
}
