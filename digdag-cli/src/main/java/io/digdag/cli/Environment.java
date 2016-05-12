package io.digdag.cli;

import java.util.Map;

public class Environment
{
    private final Map<String, String> env;

    public Environment(Map<String, String> env)
    {
        this.env = env;
    }

    public String getEnvironmentVariable(String name)
    {
        return env.get(name);
    }

    public String getProperty(String name)
    {
        return System.getProperty(name);
    }
}
