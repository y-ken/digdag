package io.digdag.cli.client;

import com.beust.jcommander.DynamicParameter;
import com.beust.jcommander.Parameter;
import com.google.common.base.Optional;
import com.treasuredata.client.ProxyConfig;
import io.digdag.cli.Command;
import io.digdag.cli.Main;
import io.digdag.cli.SystemExitException;
import io.digdag.cli.YamlMapper;
import io.digdag.client.DigdagClient;
import io.digdag.core.Version;

import io.digdag.standards.Proxies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.core.Response;

import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import static io.digdag.cli.SystemExitException.systemExit;

public abstract class ClientCommand
        extends Command
{
    private static final Logger logger = LoggerFactory.getLogger(ClientCommand.class);

    private static final String DEFAULT_ENDPOINT = "http://127.0.0.1:65432";

    protected final Version localVersion;

    @Parameter(names = {"-e", "--endpoint"})
    protected String endpoint = null;

    @DynamicParameter(names = {"-H", "--header"})
    Map<String, String> httpHeaders = new HashMap<>();

    @Parameter(names = {"--disable-version-check"})
    protected boolean disableVersionCheck;

    @Parameter(names = {"--disable-cert-validation"})
    protected boolean disableCertValidation;

    public ClientCommand(Map<String, String> env, Version localVersion, PrintStream out, PrintStream err)
    {
        super(env, out, err);
        this.localVersion = Objects.requireNonNull(localVersion, "localVersion");
    }

    @Override
    public void main()
            throws Exception
    {
        try {
            mainWithClientException();
        }
        catch (ClientErrorException ex) {
            Response res = ex.getResponse();
            String body;
            try {
                body = res.readEntity(String.class);
            }
            catch (Exception readFailed) {
                body = ex.getMessage();
            }
            switch (res.getStatus()) {
                case 404:  // NOT_FOUND
                    throw systemExit("Resource not found: " + body);
                case 409:  // CONFLICT
                    throw systemExit("Request conflicted: " + body);
                case 422:  // UNPROCESSABLE_ENTITY
                    throw systemExit("Invalid option: " + body);
                default:
                    throw systemExit("Status code " + res.getStatus() + ": " + body);
            }
        }
    }

    public abstract void mainWithClientException()
            throws Exception;

    protected DigdagClient buildClient()
            throws IOException, SystemExitException
    {
        return buildClient(true);
    }

    protected DigdagClient buildClient(boolean checkServerVersion)
            throws IOException, SystemExitException
    {
        // load config file
        Properties props = loadSystemProperties();

        if (endpoint == null) {
            endpoint = props.getProperty("client.http.endpoint", DEFAULT_ENDPOINT);
        }

        String[] fragments = endpoint.split(":", 2);

        boolean useSsl = false;
        if (fragments.length == 2 && fragments[1].startsWith("//")) {
            // http:// or https://
            switch (fragments[0]) {
                case "http":
                    useSsl = false;
                    break;
                case "https":
                    useSsl = true;
                    break;
                default:
                    throw systemExit("Endpoint must start with http:// or https://: " + endpoint);
            }
            fragments = fragments[1].substring(2).split(":", 2);
        }

        String host;
        int port;
        if (fragments.length == 1) {
            host = fragments[0];
            port = useSsl ? 443 : 80;
        }
        else {
            host = fragments[0];
            String portString = fragments[1].split("/", 2)[0];
            port = Integer.parseInt(portString);
        }

        Map<String, String> headers = new HashMap<>();
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("client.http.headers.")) {
                headers.put(key.substring("client.http.headers.".length()), props.getProperty(key));
            }
        }
        headers.putAll(this.httpHeaders);

        String scheme = useSsl ? "https" : "http";
        logger.debug("Using endpoint {}://{}:{}", scheme, host, port);

        DigdagClient.Builder builder = DigdagClient.builder()
                .host(host)
                .port(port)
                .ssl(useSsl)
                .headers(headers);

        if (disableCertValidation) {
            builder.disableCertValidation(true);
        }

        Optional<ProxyConfig> proxyConfig = Proxies.proxyConfigFromEnv(scheme, env);
        if (proxyConfig.isPresent()) {
            ProxyConfig cfg = proxyConfig.get();
            if (cfg.getUser().isPresent() || cfg.getPassword().isPresent()) {
                logger.warn("HTTP proxy authentication not supported. Ignoring proxy username and password.");
            }
            builder.proxyHost(cfg.getHost());
            builder.proxyPort(cfg.getPort());
            builder.proxyScheme(cfg.useSSL() ? "https" : "http");
        }

        DigdagClient client = builder.build();

        if (checkServerVersion && !disableVersionCheck) {
            Map<String, Object> remoteVersions = client.getVersion();
            String remoteVersion = String.valueOf(remoteVersions.getOrDefault("version", ""));

            if (!localVersion.version().equals(remoteVersion)) {
                throw systemExit(String.format(""
                                + "Client and server version mismatch: Client: %s, Server: %s.%n"
                                + "Please run following command locally to download a compatible version with the server:%n"
                                + "%n"
                                + "    digdag selfupdate %s%n",
                        localVersion, remoteVersion, remoteVersion));
            }
        }

        return client;
    }

    public void showCommonOptions()
    {
        err.println("    -e, --endpoint HOST[:PORT]       HTTP endpoint (default: http://127.0.0.1:65432)");
        Main.showCommonOptions(env, err);
    }

    protected long parseLongOrUsage(String arg)
            throws SystemExitException
    {
        try {
            return Long.parseLong(args.get(0));
        }
        catch (NumberFormatException ex) {
            throw usage(ex.getMessage());
        }
    }

    protected int parseIntOrUsage(String arg)
            throws SystemExitException
    {
        try {
            return Integer.parseInt(args.get(0));
        }
        catch (NumberFormatException ex) {
            throw usage(ex.getMessage());
        }
    }

    protected void ln(String format, Object... args)
    {
        out.println(String.format(format, args));
    }

    protected static YamlMapper yamlMapper()
    {
        return new YamlMapper(DigdagClient.objectMapper());
    }
}
