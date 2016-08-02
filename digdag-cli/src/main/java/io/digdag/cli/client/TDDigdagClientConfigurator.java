package io.digdag.cli.client;

import com.treasuredata.client.TDClientConfig;
import io.digdag.client.DigdagClient;
import io.digdag.spi.ClientConfigurator;

import java.io.File;
import java.util.Properties;

import static com.treasuredata.client.TDClientConfig.Type.APIKEY;

public class TDDigdagClientConfigurator
        implements ClientConfigurator
{
    @Override
    public void configureClient(DigdagClient.Builder builder)
    {
        File file = new File(System.getProperty("user.home", "./"), ".td/td.conf");
        if (!file.exists()) {
            return;
        }

        Properties p = TDClientConfig.readTDConf(file);
        String apikey = p.getProperty(APIKEY.key, p.getProperty("apikey"));

        if (apikey != null) {
            builder.host("api-workflow.treasuredata.com");
            builder.ssl(true);
            builder.header("Authorization", "TD1 " + apikey);
        }
    }
}
