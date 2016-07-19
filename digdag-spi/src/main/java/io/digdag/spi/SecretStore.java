package io.digdag.spi;

public interface SecretStore
{
    String getSecret(SecretAccessContext context, String key);
}
