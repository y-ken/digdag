package acceptance;

import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharSource;
import io.digdag.client.DigdagClient;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import utils.CommandStatus;
import utils.TemporaryDigdagServer;
import utils.TestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static utils.TestUtils.attemptFailure;
import static utils.TestUtils.attemptSuccess;
import static utils.TestUtils.copyResource;
import static utils.TestUtils.expect;
import static utils.TestUtils.getAttemptLogs;
import static utils.TestUtils.main;
import static utils.TestUtils.pushProject;
import static utils.TestUtils.startWorkflow;

public class SecretsIT
{
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Rule
    public TemporaryDigdagServer server = TemporaryDigdagServer.builder()
            .configuration("digdag.secret-encryption-key = " + Base64.getEncoder().encodeToString(RandomUtils.nextBytes(16)))
            .build();

    private Path config;
    private Path projectDir;
    private DigdagClient client;

    @Before
    public void setUp()
            throws Exception
    {
        projectDir = folder.newFolder().toPath().toAbsolutePath().normalize();
        config = folder.newFile().toPath();

        client = DigdagClient.builder()
                .host(server.host())
                .port(server.port())
                .build();
    }

    @Test
    public void testSetListProjectSecrets()
            throws Exception
    {
        String projectName = "test";

        // Push the project
        CommandStatus pushStatus = main("push",
                "--project", projectDir.toString(),
                projectName,
                "-c", config.toString(),
                "-e", server.endpoint());
        assertThat(pushStatus.errUtf8(), pushStatus.code(), is(0));

        // Command line args
        String key1 = "key1";
        String value1 = "value1";
        String key2 = "key2";
        String value2 = "value2";

        // Single file value
        String key3 = "key3";
        String value3 = "value3";

        // Single stdin value
        String key4 = "key4";
        String value4 = "value4";

        // Multiple stdin values
        String key5 = "key5";
        String value5 = "value5";
        String key6 = "key6";
        String value6 = "value6";

        // Multiple file values
        String key7 = "key7";
        String value7 = "value7";
        String key8 = "key8";
        String value8 = "value8";

        Path value3File = folder.newFile().toPath();
        Files.write(value3File, ImmutableList.of(value3));

        // Set secrets on command line and from file
        {
            CommandStatus setStatus = main("secrets",
                    "-c", config.toString(),
                    "-e", server.endpoint(),
                    projectName,
                    "--set",
                    key1 + '=' + value1,
                    key2 + '=' + value2,
                    key3 + "=@" + value3File.toString());
            assertThat(setStatus.errUtf8(), setStatus.code(), is(0));
        }

        // Set secret from stdin
        {
            InputStream in = new ByteArrayInputStream(value4.getBytes(US_ASCII));
            CommandStatus setStatus = main(
                    in,
                    "secrets",
                    "-c", config.toString(),
                    "-e", server.endpoint(),
                    projectName,
                    "--set",
                    key4 + "=-");
            assertThat(setStatus.errUtf8(), setStatus.code(), is(0));
        }

        YAMLFactory yamlFactory = new YAMLFactory();

        // Set secrets from stdin
        {
            ByteArrayOutputStream yaml = new ByteArrayOutputStream();
            YAMLGenerator generator = yamlFactory.createGenerator(yaml);
            TestUtils.objectMapper().writeValue(generator, ImmutableMap.of(key5, value5, key6, value6));
            InputStream in = new ByteArrayInputStream(yaml.toByteArray());

            CommandStatus setStatus = main(
                    in,
                    "secrets",
                    "-c", config.toString(),
                    "-e", server.endpoint(),
                    projectName,
                    "--set", "@-");
            assertThat(setStatus.errUtf8(), setStatus.code(), is(0));
        }

        // Set secrets from file
        Path secretsFileYaml = folder.newFolder().toPath().resolve("secrets.yaml");
        try (OutputStream o = Files.newOutputStream(secretsFileYaml)) {
            YAMLGenerator generator = yamlFactory.createGenerator(o);
            TestUtils.objectMapper().writeValue(generator, ImmutableMap.of(key7, value7, key8, value8));

            CommandStatus setStatus = main(
                    "secrets",
                    "-c", config.toString(),
                    "-e", server.endpoint(),
                    projectName,
                    "--set", "@" + secretsFileYaml);
            assertThat(setStatus.errUtf8(), setStatus.code(), is(0));
        }

        // List secrets
        CommandStatus listStatus = main("secrets",
                "-c", config.toString(),
                "-e", server.endpoint(),
                projectName);
        assertThat(listStatus.errUtf8(), listStatus.code(), is(0));

        List<String> listedKeys = CharSource.wrap(listStatus.outUtf8()).readLines();
        assertThat(listedKeys, containsInAnyOrder(key1, key2, key3, key4, key5, key6, key7, key8));
    }

    @Test
    public void testUseProjectSecret()
            throws Exception
    {
        String projectName = "test";

        Path outfile = folder.newFolder().toPath().toAbsolutePath().normalize().resolve("out");
        Path outfileParameterized = folder.newFolder().toPath().toAbsolutePath().normalize().resolve("out-parameterized");

        // Push the project
        copyResource("acceptance/secrets/echo_secret.dig", projectDir);
        copyResource("acceptance/secrets/echo_secret_parameterized.dig", projectDir);
        pushProject(server.endpoint(), projectDir, projectName);

        String key1 = "key1";
        String key2 = "key2";

        String value1 = "value1";
        String value2 = "value2";

        // Set secrets
        CommandStatus setStatus = main("secrets",
                "-c", config.toString(),
                "-e", server.endpoint(),
                projectName,
                "--set", key1 + '=' + value1, key2 + '=' + value2);
        assertThat(setStatus.errUtf8(), setStatus.code(), is(0));

        long attemptId = startWorkflow(
                server.endpoint(), projectName, "echo_secret",
                ImmutableMap.of("OUTFILE", outfile.toString()));

        long attemptIdParameterized = startWorkflow(
                server.endpoint(), projectName, "echo_secret_parameterized",
                ImmutableMap.of("secret_key", "key2",
                        "OUTFILE", outfileParameterized.toString()));

        expect(Duration.ofSeconds(30), attemptSuccess(server.endpoint(), attemptId));
        expect(Duration.ofSeconds(30), attemptSuccess(server.endpoint(), attemptIdParameterized));

        List<String> output = Files.readAllLines(outfile);
        assertThat(output, contains(value1));

        List<String> outputParameterized = Files.readAllLines(outfileParameterized);
        assertThat(outputParameterized, contains(value2));
    }

    @Test
    public void verifyInvalidSecretUseFails()
            throws Exception
    {
        String projectName = "test";

        // Push the project
        copyResource("acceptance/secrets/invalid_secret_use.dig", projectDir);
        copyResource("acceptance/secrets/echo_secret_parameterized.dig", projectDir);
        pushProject(server.endpoint(), projectDir, projectName);

        String key1 = "key1";
        String key2 = "key2";

        String value1 = "value1";
        String value2 = "value2";

        // Set secrets
        CommandStatus setStatus = main("secrets",
                "-c", config.toString(),
                "-e", server.endpoint(),
                projectName,
                "--set", key1 + '=' + value1, key2 + '=' + value2);
        assertThat(setStatus.errUtf8(), setStatus.code(), is(0));

        long attemptId = startWorkflow(server.endpoint(), projectName, "invalid_secret_use", ImmutableMap.of());

        expect(Duration.ofSeconds(30), attemptFailure(server.endpoint(), attemptId));

        String logs = getAttemptLogs(client, attemptId);
        assertThat(logs, containsString("\"key1\" is not defined"));
    }
}