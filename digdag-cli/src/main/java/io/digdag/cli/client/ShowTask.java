package io.digdag.cli.client;

import io.digdag.cli.Environment;
import io.digdag.cli.SystemExitException;
import io.digdag.client.DigdagClient;
import io.digdag.client.api.RestTask;
import io.digdag.core.Version;

import java.io.PrintStream;

import static io.digdag.cli.SystemExitException.systemExit;

public class ShowTask
    extends ClientCommand
{
    public ShowTask(Version version, Environment environment)
    {
        super(version, environment);
    }

    @Override
    public void mainWithClientException()
        throws Exception
    {
        if (args.size() != 1) {
            throw usage(null, environment);
        }
        showTasks(parseLongOrUsage(args.get(0)));
    }

    public SystemExitException usage(String error, Environment environment)
    {
        err.println("Usage: digdag tasks <attempt-id>");
        err.println("  Options:");
        showCommonOptions();
        return systemExit(error);
    }

    private void showTasks(long attemptId)
        throws Exception
    {
        DigdagClient client = buildClient();

        int count = 0;
        for (RestTask task : client.getTasks(attemptId)) {
            ln("   id: %d", task.getId());
            ln("   name: %s", task.getFullName());
            ln("   state: %s", task.getState());
            ln("   config: %s", task.getConfig());
            ln("   parent: %d", task.getParentId().orNull());
            ln("   upstreams: %s", task.getUpstreams());
            ln("   export params: %s", task.getExportParams());
            ln("   store params: %s", task.getStoreParams());
            ln("   state params: %s", task.getStateParams());
            ln("");
            count++;
        }

        if (count == 0) {
            client.getSessionAttempt(attemptId);  // throws exception if attempt doesn't exist
        }
        ln("%d entries.", count);
    }
}
