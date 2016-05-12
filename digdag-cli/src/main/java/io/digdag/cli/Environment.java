package io.digdag.cli;

import java.io.PrintStream;
import java.util.Map;

public class Environment
{
    private final Map<String, String> env;
    private final PrintStream out;
    private final PrintStream err;

    public Environment(Map<String, String> env, PrintStream out, PrintStream err)
    {
        this.env = env;
        this.out = out;
        this.err = err;
    }

    public String environmentVariable(String name)
    {
        return env.get(name);
    }

    public String systemProperty(String name)
    {
        return System.getProperty(name);
    }

    public PrintStream out()
    {
        return out;
    }

    public PrintStream err()
    {
        return err;
    }
}
