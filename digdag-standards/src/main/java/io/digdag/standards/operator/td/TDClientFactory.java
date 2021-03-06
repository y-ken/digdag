package io.digdag.standards.operator.td;

import com.google.common.base.Optional;
import com.google.common.annotations.VisibleForTesting;
import com.treasuredata.client.ProxyConfig;
import com.treasuredata.client.TDClient;
import com.treasuredata.client.TDClientBuilder;
import io.digdag.client.config.Config;
import io.digdag.client.config.ConfigException;

class TDClientFactory
{
    @VisibleForTesting
    static TDClientBuilder clientBuilderFromConfig(Config params)
    {
        String apikey = params.get("apikey", String.class).trim();
        if (apikey.isEmpty()) {
            throw new ConfigException("Parameter 'apikey' is empty");
        }

        TDClientBuilder builder = TDClient.newBuilder(false);

        Config proxyConfig = params.getNestedOrGetEmpty("proxy");
        boolean proxyEnabled = proxyConfig.get("enabled", Boolean.class, false);
        if (proxyEnabled) {
            builder.setProxy(proxyConfig(proxyConfig));
        }

        return builder
                .setEndpoint(params.get("endpoint", String.class, "api.treasuredata.com"))
                .setUseSSL(params.get("use_ssl", boolean.class, true))
                .setApiKey(apikey)
                .setRetryLimit(0)  // disable td-client's retry mechanism
                ;
    }

    static TDClient clientFromConfig(Config params)
    {
        return clientBuilderFromConfig(params).build();
    }

    private static ProxyConfig proxyConfig(Config config)
    {
        ProxyConfig.ProxyConfigBuilder builder = new ProxyConfig.ProxyConfigBuilder();

        Optional<String> host = config.getOptional("host", String.class);
        if (host.isPresent()) {
            builder.setHost(host.get());
        }

        Optional<Integer> port = config.getOptional("port", Integer.class);
        if (port.isPresent()) {
            builder.setPort(port.get());
        }

        Optional<String> user = config.getOptional("user", String.class);
        if (user.isPresent()) {
            builder.setUser(user.get());
        }

        Optional<String> password = config.getOptional("password", String.class);
        if (password.isPresent()) {
            builder.setPassword(password.get());
        }

        Optional<Boolean> useSsl = config.getOptional("use_ssl", Boolean.class);
        if (useSsl.isPresent()) {
            builder.useSSL(useSsl.get());
        }

        return builder.createProxyConfig();
    }
}
