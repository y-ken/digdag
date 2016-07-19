package io.digdag.core.database;

import io.digdag.core.SecretCrypto;
import io.digdag.spi.SecretControlStore;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

import java.util.List;

class DatabaseSecretControlStore
        extends BasicDatabaseStoreManager<DatabaseSecretControlStore.Dao>
        implements SecretControlStore
{
    private final int siteId;
    private final SecretCrypto crypto;

    DatabaseSecretControlStore(DatabaseConfig config, DBI dbi, int siteId, SecretCrypto crypto)
    {
        super(config.getType(), Dao.class, dbi);
        this.siteId = siteId;
        this.crypto = crypto;
    }

    @Override
    public void setProjectSecret(int projectId, String key, String value)
    {
        String encrypted = crypto.encryptSecret(value);

        transaction((handle, dao, ts) -> {
            dao.deleteProjectSecret(siteId, projectId, key);
            dao.insertProjectSecret(siteId, projectId, key, encrypted);
            return null;
        });
    }

    @Override
    public void deleteProjectSecret(int projectId, String key)
    {
        transaction((handle, dao, ts) -> {
            dao.deleteProjectSecret(siteId, projectId, key);
            return null;
        });
    }

    @Override
    public List<String> listProjectSecrets(int projectId)
    {
        return transaction((handle, dao, ts) -> dao.listProjectSecrets(siteId, projectId));
    }

    interface Dao
    {
        @SqlQuery("select key from secrets" +
                " where site_id = :siteId and project_id = :projectId")
        List<String> listProjectSecrets(@Bind("siteId") int siteId, @Bind("projectId") int projectId);

        @SqlUpdate("delete from secrets" +
                " where site_id = :siteId and project_id = :projectId and key = :key")
        int deleteProjectSecret(@Bind("siteId") int siteId, @Bind("projectId") int projectId, @Bind("key") String key);

        @SqlUpdate("insert into secrets" +
                " (site_id, project_id, key, value, updated_at)" +
                " values (:siteId, :projectId, :key, :value, now())")
        int insertProjectSecret(@Bind("siteId") int siteId, @Bind("projectId") int projectId, @Bind("key") String key, @Bind("value") String value);
    }
}
