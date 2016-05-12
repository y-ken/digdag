package acceptance;

import com.google.common.collect.ImmutableMap;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static acceptance.TestUtils.copyResource;
import static acceptance.TestUtils.main;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class ConfigIT
{
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path root()
    {
        return folder.getRoot().toPath().toAbsolutePath();
    }

    @Test
    public void propertyByFile()
            throws Exception
    {
        copyResource("acceptance/params.yml", root().resolve("params.yml"));

        Path home = root().resolve("home");
        Path configHome = home.resolve(".config");
        Path configDirectory = configHome.resolve("digdag");
        Path configFile = configDirectory.resolve("config");

        Files.createDirectories(configDirectory);
        Files.write(configFile, "params.mysql.password=secret".getBytes(UTF_8));

        TestUtils.fakeHome(home.toString(), () -> {
            main("run", "-o", root().toString(), "-f", root().resolve("params.yml").toString());
        });

        assertThat(Files.readAllBytes(root().resolve("foo.out")), is("secret\n".getBytes(UTF_8)));
    }

    @Test
    public void verifyThatXdgConfigHomeEnvVarIsRespected()
            throws Exception
    {
        copyResource("acceptance/params.yml", root().resolve("params.yml"));

        Path home = root().resolve("home");
        Path configHome = home.resolve("etc");
        Path configDirectory = configHome.resolve("digdag");
        Path configFile = configDirectory.resolve("config");

        Files.createDirectories(configDirectory);
        Files.write(configFile, "params.mysql.password=secret".getBytes(UTF_8));

        Map<String, String> env = ImmutableMap.of("XDG_CONFIG_HOME", configHome.toAbsolutePath().toString());

        TestUtils.fakeHome(home.toString(), () -> {
            main(env, "run", "-o", root().toString(), "-f", root().resolve("params.yml").toString());
        });

        assertThat(Files.readAllBytes(root().resolve("foo.out")), is("secret\n".getBytes(UTF_8)));
    }

    @Test
    public void verifyThatCommandLineParamsOverridesConfigFileParams()
            throws Exception
    {
        copyResource("acceptance/params.yml", root().resolve("params.yml"));

        Path home = root().resolve("home");
        Path configHome = home.resolve(".config");
        Path configDirectory = configHome.resolve("digdag");
        Path configFile = configDirectory.resolve("config");

        Files.createDirectories(configDirectory);
        Files.write(configFile, "params.mysql.password=secret".getBytes(UTF_8));

        TestUtils.fakeHome(home.toString(), () -> {
            main("run",
                    "-o", root().toString(),
                    "-f", root().resolve("params.yml").toString(),
                    "-p", "mysql.password=override");
        });

        assertThat(Files.readAllLines(root().resolve("foo.out")), contains("override"));
    }
}
