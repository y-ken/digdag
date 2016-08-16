package io.digdag.standards.operator.td;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.treasuredata.client.model.TDJobRequest;
import com.treasuredata.client.model.TDJobRequestBuilder;
import io.digdag.client.config.Config;
import io.digdag.client.config.ConfigElement;
import io.digdag.client.config.ConfigException;
import io.digdag.core.Environment;
import io.digdag.spi.Operator;
import io.digdag.spi.OperatorFactory;
import io.digdag.spi.TaskExecutionException;
import io.digdag.spi.TaskRequest;
import io.digdag.spi.TaskResult;
import io.digdag.spi.TemplateEngine;
import io.digdag.util.BaseOperator;
import org.msgpack.value.ArrayValue;
import org.msgpack.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

public class TdWaitOperatorFactory
        extends AbstractWaitOperatorFactory
        implements OperatorFactory
{
    private static Logger logger = LoggerFactory.getLogger(TdWaitOperatorFactory.class);
    
    private static final String POLL_JOB = "pollJob";

    private final TemplateEngine templateEngine;
    private final Map<String, String> env;

    @Inject
    public TdWaitOperatorFactory(TemplateEngine templateEngine, Config systemConfig, @Environment Map<String, String> env)
    {
        super(systemConfig);
        this.templateEngine = templateEngine;
        this.env = env;
    }

    public String getType()
    {
        return "td_wait";
    }

    @Override
    public Operator newTaskExecutor(Path workspacePath, TaskRequest request)
    {
        return new TdWaitOperator(workspacePath, request);
    }

    private class TdWaitOperator
            extends BaseOperator
    {
        private final Config params;
        private final String query;
        private final int pollInterval;
        private final String engine;
        private final int priority;
        private final int jobRetry;
        private final Config state;

        private TdWaitOperator(Path workspacePath, TaskRequest request)
        {
            super(workspacePath, request);

            this.params = request.getConfig().mergeDefault(
                    request.getConfig().getNestedOrGetEmpty("td"));
            this.query = templateEngine.templateCommand(workspacePath, params, "query", UTF_8);
            this.pollInterval = getPollInterval(params);
            this.engine = params.get("engine", String.class, "presto");
            if (!engine.equals("presto") && !engine.equals("hive")) {
                throw new ConfigException("Unknown 'engine:' option (available options are: hive and presto): " + engine);
            }
            this.priority = params.get("priority", int.class, 0);  // TODO this should accept string (VERY_LOW, LOW, NORMAL, HIGH VERY_HIGH)
            this.jobRetry = params.get("job_retry", int.class, 0);
            this.state = request.getLastStateParams().deepCopy();
        }

        @Override
        public TaskResult runTask()
        {
            try (TDOperator op = TDOperator.fromConfig(params, env)) {

                TDJobOperator job = op.runJob(state, POLL_JOB, this::startJob);
                state.remove(POLL_JOB);

                // Fetch the job output to see if the query condition has been fulfilled
                logger.debug("fetching poll job result: {}", job.getJobId());
                boolean done = fetchJobResult(job);

                // If the query condition was not fulfilled, go back to sleep.
                if (!done) {
                    throw TaskExecutionException.ofNextPolling(pollInterval, ConfigElement.copyOf(state));
                }

                // The query condition was fulfilled, we're done.
                return TaskResult.empty(request);
            }
        }

        private String startJob(TDOperator op, String domainKey)
        {
            TDJobRequest req = new TDJobRequestBuilder()
                    .setType(engine)
                    .setDatabase(op.getDatabase())
                    .setQuery(query)
                    .setRetryLimit(jobRetry)
                    .setPriority(priority)
                    .setScheduledTime(request.getSessionTime().getEpochSecond())
                    .setDomainKey(domainKey)
                    .createTDJobRequest();

            String jobId = op.submitNewJob(req);
            logger.info("Started {} job id={}:\n{}", engine, jobId, query);

            return jobId;
        }

        private boolean fetchJobResult(TDJobOperator job)
        {
            Optional<ArrayValue> firstRow = job.getResult(ite -> ite.hasNext() ? Optional.of(ite.next()) : Optional.absent());

            // There must be at least one row in the result for the wait condition to be fulfilled.
            if (!firstRow.isPresent()) {
                return false;
            }

            ArrayValue row = firstRow.get();
            if (row.size() < 1) {
                throw new TaskExecutionException("Got empty row in result of query", ConfigElement.empty());
            }

            Value firstCol = row.get(0);
            return isTruthy(firstCol);
        }

        private boolean isTruthy(Value firstCol)
        {
            // Anything that is not NULL and not FALSE or 0 is considered truthy.
            switch (firstCol.getValueType()) {
                case NIL:
                    return false;
                case BOOLEAN:
                    return firstCol.asBooleanValue().getBoolean();
                case INTEGER:
                    return firstCol.asIntegerValue().asLong() != 0;
                default:
                    return true;
            }
        }
    }
}
