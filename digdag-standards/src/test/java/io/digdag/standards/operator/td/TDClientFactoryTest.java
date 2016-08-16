package io.digdag.standards.operator.td;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.treasuredata.client.TDClientBuilder;
import com.treasuredata.client.TDClientConfig;
import io.digdag.client.api.JacksonTimeModule;
import io.digdag.client.config.Config;
import io.digdag.client.config.ConfigFactory;
import org.junit.Test;

import java.net.URI;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class TDClientFactoryTest
{
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new GuavaModule())
            .registerModule(new JacksonTimeModule());
    private final ConfigFactory configFactory = new ConfigFactory(mapper);

    private Config newConfig()
    {
        return configFactory.create();
    }

    @Test
    public void testProxyConfig()
    {
        Config config = newConfig()
                .set("apikey", "foobar")
                .set("proxy",
                        newConfig()
                                .set("enabled", "true")
                                .set("host", "example.com")
                                .set("port", "9119")
                                .set("user", "me")
                                .set("password", "'(#%")
                                .set("use_ssl", true));

        TDClientBuilder builder = TDClientFactory.clientBuilderFromConfig(config, ImmutableMap.of());
        TDClientConfig clientConfig = builder.buildConfig();

        assertThat(clientConfig.proxy.get().getUser(), is(Optional.of("me")));
        assertThat(clientConfig.proxy.get().getPassword(), is(Optional.of("'(#%")));
        assertThat(clientConfig.proxy.get().getUri(), is(URI.create("https://example.com:9119")));
    }

    @Test
    public void testProxyConfigFromEnv()
    {
        Map<String, String> env = ImmutableMap.of("http_proxy", "https://me:%27(%23%25@example.com:9119");
        Config config = newConfig()
                .set("apikey", "foobar");

        TDClientBuilder builder = TDClientFactory.clientBuilderFromConfig(config, env);
        TDClientConfig clientConfig = builder.buildConfig();

        assertThat(clientConfig.proxy.get().getUser(), is(Optional.of("me")));
        assertThat(clientConfig.proxy.get().getPassword(), is(Optional.of("'(#%")));
        assertThat(clientConfig.proxy.get().getUri(), is(URI.create("https://example.com:9119")));
    }
}