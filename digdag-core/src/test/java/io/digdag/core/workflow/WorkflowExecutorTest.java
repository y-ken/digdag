package io.digdag.core.workflow;

import java.util.*;
import java.nio.file.Files;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import org.skife.jdbi.v2.IDBI;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.*;
import com.google.common.io.Resources;
import io.digdag.core.LocalSite;
import io.digdag.core.archive.*;
import io.digdag.core.repository.*;
import io.digdag.core.schedule.*;
import io.digdag.core.session.*;
import io.digdag.core.workflow.*;
import io.digdag.core.config.YamlConfigLoader;
import io.digdag.spi.ScheduleTime;
import io.digdag.client.config.Config;
import io.digdag.client.config.ConfigException;
import io.digdag.client.config.ConfigFactory;
import io.digdag.core.DigdagEmbed;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import static java.nio.charset.StandardCharsets.UTF_8;
import static io.digdag.core.workflow.WorkflowTestingUtils.setupEmbed;
import static org.junit.Assert.*;
import static org.hamcrest.Matchers.is;

public class WorkflowExecutorTest
{
    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private DigdagEmbed embed;
    private ConfigFactory cf;
    private LocalSite localSite;
    private YamlConfigLoader configLoader;

    @Before
    public void setUp()
        throws Exception
    {
        this.embed = setupEmbed();
        this.cf = embed.getInjector().getInstance(ConfigFactory.class);
        this.localSite = embed.getInjector().getInstance(LocalSite.class);
        this.configLoader = embed.getInjector().getInstance(YamlConfigLoader.class);
    }

    @After
    public void destroy()
        throws Exception
    {
        embed.close();
    }

    @Test
    public void run()
        throws Exception
    {
        runWorkflow("basic", loadYamlResource("/digdag/workflow/cases/basic.dig"));
    }

    @Test
    public void validateUnknownTopLevelKeys()
        throws Exception
    {
        exception.expect(ConfigException.class);
        runWorkflow("unknown_schedule", cf.create()
                .set("invalid_schedule", cf.create().set("daily>", "00:00:00"))
                .set("+step1", cf.create().set("sh>", "echo ok"))
                );
    }

    @Test
    public void retryOnGroupingTask()
        throws Exception
    {
        runWorkflow("retry_on_group", loadYamlResource("/digdag/workflow/cases/retry_on_group.dig"));
        assertThat(new String(Files.readAllBytes(folder.getRoot().toPath().resolve("out")), UTF_8), is("trytrytrytry"));
    }

    private Config loadYamlResource(String name)
    {
        try {
            String content = Resources.toString(getClass().getResource(name), UTF_8);
            return configLoader.loadString(content).toConfig(cf);
        }
        catch (IOException ex) {
            throw Throwables.propagate(ex);
        }
    }

    private void runWorkflow(String workflowName, Config config)
        throws InterruptedException
    {
        WorkflowTestingUtils.runWorkflow(localSite, folder.getRoot().toPath(), workflowName, config);
    }
}
