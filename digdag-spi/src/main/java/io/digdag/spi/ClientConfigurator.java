package io.digdag.spi;

import io.digdag.client.DigdagClient;

public interface ClientConfigurator
{
    void configureClient(DigdagClient.Builder builder);
}
