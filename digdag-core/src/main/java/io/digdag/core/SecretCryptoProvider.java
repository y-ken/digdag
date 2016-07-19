package io.digdag.core;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Provider;
import io.digdag.client.config.Config;
import io.digdag.core.database.AESGCMSecretCrypto;
import io.digdag.core.database.DisabledSecretCrypto;

public class SecretCryptoProvider
        implements Provider<SecretCrypto>
{
    private final SecretCrypto crypto;

    @Inject
    public SecretCryptoProvider(Config systemConfig)
    {
        Optional<String> encryptionKey = systemConfig.getOptional("digdag.secret-encryption-key", String.class);
        if (encryptionKey.isPresent()) {
            this.crypto = new AESGCMSecretCrypto(encryptionKey.get());
        }
        else {
            this.crypto = new DisabledSecretCrypto();
        }
    }

    @Override
    public SecretCrypto get()
    {
        return crypto;
    }
}
