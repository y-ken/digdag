package io.digdag.cli.client;

import io.digdag.cli.Context;
import io.digdag.core.Version;

public class ShowAttempt
    extends ShowSession
{
    public ShowAttempt(Context ctx)
    {
        super(ctx);
    }

    @Override
    protected boolean includeRetries()
    {
        return true;
    }
}
