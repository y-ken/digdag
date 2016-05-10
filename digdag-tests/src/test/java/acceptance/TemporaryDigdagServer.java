package acceptance;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.digdag.client.DigdagClient;
import io.digdag.core.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.ProcessingException;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

import static acceptance.TestUtils.main;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;

public class TemporaryDigdagServer
{
    private static final Logger log = LoggerFactory.getLogger(TemporaryDigdagServer.class);

    private static final ThreadFactory DAEMON_THREAD_FACTORY = new ThreadFactoryBuilder().setDaemon(true).build();

    private final Version version;

    private final String host;
    private final int port;
    private final String endpoint;

    private final ExecutorService executor;
    private final String configuration;

    private Path configFile;
    private Path taskLog;
    private Path accessLog;

    public TemporaryDigdagServer(Builder builder)
    {
        this.version = Objects.requireNonNull(builder.version, "version");

        this.host = "localhost";
        this.port = 65432;
        this.endpoint = "http://" + host + ":" + port;
        this.configuration = builder.configuration;

        this.executor = Executors.newSingleThreadExecutor(DAEMON_THREAD_FACTORY);
    }

    void start(Path workdir)
            throws Throwable
    {
        try {
            this.taskLog = Files.createTempDirectory(workdir, "task-log");
            this.accessLog = Files.createTempDirectory(workdir, "access-log");
            this.configFile = Files.createFile(workdir.resolve("config"));
            Files.write(configFile, configuration.getBytes("UTF-8"));
        }
        catch (IOException e) {
            throw Throwables.propagate(e);
        }

        executor.execute(() -> main(
                version,
                "server",
                "-m",
                "--task-log", taskLog.toString(),
                "--access-log", accessLog.toString(),
                "-c", configFile.toString()));

        // Poll and wait for server to come up
        for (int i = 0; i < 30; i++) {
            DigdagClient client = DigdagClient.builder()
                    .host(host)
                    .port(port)
                    .build();
            try {
                client.getProjects();
                break;
            }
            catch (ProcessingException e) {
                assertThat(e.getCause(), instanceOf(ConnectException.class));
                log.debug("Waiting for server to come up...");
            }
            Thread.sleep(1000);
        }
    }

    void stop() {
        executor.shutdownNow();
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public String endpoint()
    {
        return endpoint;
    }

    public String host()
    {
        return host;
    }

    public int port()
    {
        return port;
    }

    public static class Builder
    {
        private final Consumer<TemporaryDigdagServer> consumer;

        private Version version = Version.buildVersion();
        private String configuration = "";

        public Builder()
        {
            this.consumer = null;
        }

        public Builder(Consumer<TemporaryDigdagServer> consumer)
        {
            this.consumer = consumer;
        }

        public Builder version(Version version)
        {
            this.version = version;
            return this;
        }

        public Builder configuration(String configuration) {
            this.configuration = configuration;
            return this;
        }

        TemporaryDigdagServer build()
        {
            TemporaryDigdagServer server = new TemporaryDigdagServer(this);
            if (consumer != null) {
                consumer.accept(server);
            }
            return server;
        }
    }

    @Override
    public String toString()
    {
        return "TemporaryDigdagServer{" +
                "version=" + version +
                ", host='" + host + '\'' +
                ", port=" + port +
                '}';
    }
}
