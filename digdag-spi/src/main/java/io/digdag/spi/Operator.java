package io.digdag.spi;

import com.google.common.collect.ImmutableList;

import java.util.List;

public interface Operator
{
    // TODO: scrap backwards compatibility?
    @Deprecated
    default TaskResult run() {
        throw new UnsupportedOperationException();
    }

    default TaskResult run(TaskExecutionContext ctx) {
        return run();
    }

    default List<String> secretSelectors() {
        return ImmutableList.of();
    }
}
