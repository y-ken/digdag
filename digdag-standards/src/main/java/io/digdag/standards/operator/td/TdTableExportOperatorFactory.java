package io.digdag.standards.operator.td;

import com.google.inject.Inject;
import com.treasuredata.client.model.TDExportFileFormatType;
import com.treasuredata.client.model.TDExportJobRequest;
import io.digdag.client.config.Config;
import io.digdag.client.config.ConfigException;
import io.digdag.core.Environment;
import io.digdag.spi.Operator;
import io.digdag.spi.OperatorFactory;
import io.digdag.spi.TaskRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Map;

import static java.util.Locale.ENGLISH;

public class TdTableExportOperatorFactory
        implements OperatorFactory
{
    private static Logger logger = LoggerFactory.getLogger(TdTableExportOperatorFactory.class);
    private final Map<String, String> env;

    @Inject
    public TdTableExportOperatorFactory(@Environment Map<String, String> env)
    { this.env = env;}

    public String getType()
    {
        return "td_table_export";
    }

    @Override
    public Operator newTaskExecutor(Path workspacePath, TaskRequest request)
    {
        return new TdTableExportOperator(workspacePath, request);
    }

    private class TdTableExportOperator
            extends BaseTdJobOperator
    {
        private final String database;
        private final TableParam table;
        private final TDExportFileFormatType fileFormat;

        private TdTableExportOperator(Path workspacePath, TaskRequest request)
        {
            super(workspacePath, request, env);
            Config params = request.getConfig().mergeDefault(
                    request.getConfig().getNestedOrGetEmpty("td"));

            this.database = params.get("database", String.class);
            this.table = params.get("table", TableParam.class);

            String fileFormatString = params.get("file_format", String.class);
            try {
                this.fileFormat = TDExportFileFormatType.fromName(fileFormatString);
            }
            catch (RuntimeException ex) {
                throw new ConfigException("invalid file_format option", ex);
            }
        }

        @Override
        protected String startJob(TDOperator op, String domainKey)
        {
            TDExportJobRequest req = TDExportJobRequest.builder()
                    .database(table.getDatabase().or(database))
                    .table(table.getTable())
                    .from(Date.from(parseTime(params, "from")))
                    .to(Date.from(parseTime(params, "to")))
                    .fileFormat(fileFormat)
                    .accessKeyId(params.get("s3_access_key_id", String.class))
                    .secretAccessKey(params.get("s3_secret_access_key", String.class))
                    .bucketName(params.get("s3_bucket", String.class))
                    .filePrefix(params.get("s3_path_prefix", String.class))
                    .poolName(params.getOptional("pool_name", String.class))
                    .build();

            String jobId = op.submitNewJobWithRetry(client -> client.submitExportJob(req));
            logger.info("Started table export job id={}", jobId);

            return jobId;
        }
    }

    private static final DateTimeFormatter TIME_PARSER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[ Z]", ENGLISH);

    private static Instant parseTime(Config params, String key)
    {
        try {
            return Instant.ofEpochSecond(
                    params.get(key, long.class)
            );
        }
        catch (ConfigException ex) {
            return Instant.from(
                    TIME_PARSER
                            .withZone(params.get("timezone", ZoneId.class))
                            .parse(params.get(key, String.class))
            );
        }
    }
}
