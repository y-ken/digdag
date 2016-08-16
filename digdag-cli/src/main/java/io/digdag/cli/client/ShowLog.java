package io.digdag.cli.client;

import java.io.PrintStream;
import java.util.List;
import java.io.IOException;
import java.util.Map;

import com.google.common.base.Optional;
import com.beust.jcommander.Parameter;
import io.digdag.cli.SystemExitException;
import io.digdag.client.DigdagClient;
import io.digdag.client.api.RestLogFileHandle;
import io.digdag.client.api.RestSessionAttempt;
import io.digdag.client.api.RestTask;
import io.digdag.core.*;
import io.digdag.core.Version;
import io.digdag.core.log.LogLevel;
import static io.digdag.cli.SystemExitException.systemExit;

public class ShowLog
    extends ClientCommand
{
    @Parameter(names = {"-v", "--verbose"})
    protected boolean verbose = false;

    @Parameter(names = {"-f", "--follow"})
    protected boolean follow = false;

    public ShowLog(Version version, Map<String, String> env, PrintStream out, PrintStream err)
    {
        super(env, version, out, err);
    }

    @Override
    public void mainWithClientException()
        throws Exception
    {
        switch (args.size()) {
        case 1:
            showLogs(parseLongOrUsage(args.get(0)), Optional.absent());
            break;
        case 2:
            showLogs(parseLongOrUsage(args.get(0)), Optional.of(args.get(1)));
            break;
        default:
            throw usage(null);
        }
    }

    public SystemExitException usage(String error)
    {
        err.println("Usage: digdag log <attempt-id> [+task name prefix]");
        err.println("    -v, --verbose                    show debug logs");
        err.println("    -f, --follow                     show new logs until attempt or task finishes");
        err.println("  Options:");
        showCommonOptions();
        return systemExit(error);
    }

    private void showLogs(long attemptId, Optional<String> taskName)
        throws Exception
    {
        DigdagClient client = buildClient();

        LogLevel level = verbose ? null : LogLevel.INFO;
        TaskLogWatcher watcher = new TaskLogWatcher(client, attemptId, level, out);

        update(client, watcher, attemptId, taskName);

        int interval = 500;
        if (follow) {
            while (true) {
                if (isFinished(client, attemptId, taskName)) {
                    break;
                }
                else {
                    Thread.sleep(interval);
                    interval = Math.min(interval * 2, 10000);
                    boolean updated = update(client, watcher, attemptId, taskName);
                    if (updated) {
                        interval = 500;
                    }
                }
            }
        }
    }

    private boolean update(DigdagClient client, TaskLogWatcher watcher,
            long attemptId, Optional<String> taskName)
        throws IOException
    {
        List<RestLogFileHandle> handles;
        if (taskName.isPresent()) {
            handles = client.getLogFileHandlesOfTask(attemptId, taskName.get());
        }
        else {
            handles = client.getLogFileHandlesOfAttempt(attemptId);
        }

        return watcher.update(handles);
    }

    private boolean isFinished(DigdagClient client, long attemptId, Optional<String> taskName)
    {
        if (taskName.isPresent()) {
            RestSessionAttempt attempt = client.getSessionAttempt(attemptId);
            if (attempt.getDone()) {
                return true;
            }
            for (RestTask task : client.getTasks(attemptId)) {
                if (task.getFullName().startsWith(taskName.get())) {
                    switch (task.getState()) {
                    case "blocked":
                    case "ready":
                    case "retry_waiting":
                    case "group_retry_waiting":
                    case "running":
                        return false;
                    case "planned":
                    case "group_error":
                    case "success":
                    case "error":
                    case "canceled":
                    default:
                    }
                }
            }
            return true;
        }
        else {
            RestSessionAttempt attempt = client.getSessionAttempt(attemptId);
            return attempt.getDone();
        }
    }
}
