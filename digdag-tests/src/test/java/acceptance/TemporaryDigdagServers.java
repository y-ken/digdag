package acceptance;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.digdag.client.DigdagClient;
import io.digdag.core.Version;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.ProcessingException;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static acceptance.TestUtils.main;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;

public class TemporaryDigdagServers
        implements TestRule
{
    private final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final List<TemporaryDigdagServer> servers = new CopyOnWriteArrayList<>();

    private TemporaryDigdagServers()
    {
    }

    public static TemporaryDigdagServers create() {
        return new TemporaryDigdagServers();
    }

    @Override
    public Statement apply(Statement base, Description description)
    {
        return RuleChain
                .outerRule(temporaryFolder)
                .around(this::statement)
                .apply(base, description);
    }

    private Statement statement(Statement statement, Description description)
    {
        return new Statement()
        {
            @Override
            public void evaluate()
                    throws Throwable
            {
                before();
                try {
                    statement.evaluate();
                }
                finally {
                    after();
                }
            }
        };
    }

    private void before()
            throws Throwable
    {
        for (TemporaryDigdagServer server : servers) {
            server.start(temporaryFolder.newFolder().toPath());
        }
    }

    private void after()
    {
        for (TemporaryDigdagServer server : servers) {
            server.stop();
        }
    }

    public TemporaryDigdagServer.Builder server() {
        return new TemporaryDigdagServer.Builder(servers::add);
    }

}
