package io.digdag.spi;

public interface SecretProvider
{
    /**
     * Get a secret identified by a key.
     *
     * @param key A key identifing the secret to get.
     * @return A secret.
     * @throws SecretAccessDeniedException if access to the secret was not permitted.
     * @throws SecretNotFoundException if a secret could not be found for the specified key.
     */
    String getSecret(String key);
}
