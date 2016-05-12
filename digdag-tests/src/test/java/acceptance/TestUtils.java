package acceptance;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import io.digdag.cli.Command;
import io.digdag.cli.Environment;
import io.digdag.cli.Main;
import io.digdag.core.Version;
import org.junit.Assert;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.Callable;

import static io.digdag.core.Version.buildVersion;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

class TestUtils
{
    static CommandStatus main(String... args)
    {
        return main(buildVersion(), ImmutableMap.of(), args);
    }

    static CommandStatus main(Version localVersion, String... args)
    {
        return main(localVersion, ImmutableMap.of(), args);
    }

    static CommandStatus main(Map<String, String> env, String... args)
    {
        return main(buildVersion(), env, args);
    }

    static CommandStatus main(Version localVersion, Map<String, String> env, String... args)
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        final int code;
        try {
            code = new Main(localVersion, new PrintStream(out), new PrintStream(err), new Environment(env)).cli(args);
        }
        catch (RuntimeException e) {
            e.printStackTrace();
            Assert.fail();
            throw e;
        }
        return CommandStatus.of(code, out.toByteArray(), err.toByteArray());
    }

    static void copyResource(String resource, Path dest) throws IOException
    {
        try (InputStream input = Resources.getResource(resource).openStream()) {
            Files.copy(input, dest, REPLACE_EXISTING);
        }
    }

    static void fakeHome(String home, Action a) throws Exception
    {
        String orig = System.setProperty("user.home", home);
        try {
            a.run();
        }
        finally {
            System.setProperty("user.home", orig);
        }
    }
}
