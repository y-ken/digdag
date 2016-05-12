package io.digdag.cli;

import java.nio.file.Path;
import java.nio.file.Paths;

class ConfigUtil
{
    static Path defaultConfigPath(Environment environment)
    {
        return configHome(environment).resolve("digdag").resolve("config");
    }

    private static Path configHome(Environment environment)
    {
        String configHome = environment.environmentVariable("XDG_CONFIG_HOME");
        if (configHome != null) {
            return Paths.get(configHome);
        }
        return Paths.get(environment.systemProperty("user.home"), ".config");
    }
}
