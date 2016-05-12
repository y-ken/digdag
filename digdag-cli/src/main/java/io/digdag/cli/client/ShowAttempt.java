package io.digdag.cli.client;

import io.digdag.cli.Environment;
import io.digdag.core.Version;

import java.io.PrintStream;

public class ShowAttempt
    extends ShowSession
{
    public ShowAttempt(Version version, PrintStream out, PrintStream err, Environment environment)
    {
        super(version, out, err, environment);
    }

    @Override
    protected boolean includeRetries()
    {
        return true;
    }
}
