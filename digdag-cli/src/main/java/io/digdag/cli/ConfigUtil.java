package io.digdag.cli;

import java.nio.file.Path;
import java.nio.file.Paths;

class ConfigUtil
{
    static Path defaultConfigPath(Context ctx)
    {
        return configHome(ctx).resolve("digdag").resolve("config");
    }

    private static Path configHome(Context ctx)
    {
        String configHome = ctx.environmentVariable("XDG_CONFIG_HOME");
        if (configHome != null) {
            return Paths.get(configHome);
        }
        return Paths.get(ctx.systemProperty("user.home"), ".config");
    }
}
