package io.digdag.cli;

import com.google.common.collect.ImmutableMap;
import io.digdag.core.Version;

import java.io.PrintStream;
import java.util.Map;
import java.util.Objects;

public class Context
{
    private io.digdag.core.Version version;
    private final Map<String, String> env;
    private final PrintStream out;
    private final PrintStream err;

    private Context(Builder builder)
    {
        this.version = Objects.requireNonNull(builder.version, "version");
        this.env = Objects.requireNonNull(builder.env, "env");
        this.out = Objects.requireNonNull(builder.out, "out");
        this.err = Objects.requireNonNull(builder.err, "err");
    }

    public Version version()
    {
        return version;
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

    public Builder toBuilder() {
        return builder()
                .version(version())
                .env(env)
                .out(out)
                .err(err());
    }

    public static Context defaultContext()
    {
        return builder().build();
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static class Builder
    {
        private Version version = Version.buildVersion();
        private Map<String, String> env = ImmutableMap.of();
        private PrintStream out = System.out;
        private PrintStream err = System.err;

        private Builder()
        {
        }

        public Builder version(Version version)
        {
            this.version = version;
            return this;
        }

        public Builder env(Map<String, String> env)
        {
            this.env = env;
            return this;
        }

        public Builder env(String key, String value)
        {
            this.env = ImmutableMap.<String, String>builder()
                    .putAll(env)
                    .put(key, value)
                    .build();
            return this;
        }

        public Builder out(PrintStream out)
        {
            this.out = out;
            return this;
        }

        public Builder err(PrintStream err)
        {
            this.err = err;
            return this;
        }

        public Context build()
        {
            return new Context(this);
        }
    }
}
