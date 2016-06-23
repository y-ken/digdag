package io.digdag.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import io.digdag.client.api.JacksonTimeModule;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;

public class CollectionPrinter<T>
{

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new GuavaModule())
            .registerModule(new JacksonTimeModule());

    private static final ObjectWriter YAML_WRITER = new ObjectMapper(new YAMLFactory()
            .configure(YAMLGenerator.Feature.WRITE_DOC_START_MARKER, false))
            .registerModule(new GuavaModule())
            .registerModule(new JacksonTimeModule())
            .writer();

    private final static ObjectWriter JSON_WRITER = MAPPER
            .writerWithDefaultPrettyPrinter();

    private final List<Column<T>> columns = new ArrayList<>();

    public void column(String name, Function<T, String> accessor)
    {
        columns.add(new Column<>(name, accessor));
    }

    public void print(OutputFormat f, List<T> items, OutputStream out)
            throws IOException
    {
        switch (f) {
            case TABLE: {
                TablePrinter table = new TablePrinter(new PrintStream(out));
                List<String> header = columns.stream()
                        .map(c -> c.name)
                        .collect(toList());
                table.row(header);
                for (T item : items) {
                    List<String> values = columns.stream()
                            .map(c -> c.accessor.apply(item))
                            .collect(toList());
                    table.row(values);
                }
                table.print();
            }
            break;
            case JSON: {
                JSON_WRITER.writeValue(out, items);
            }
            break;
            case YAML: {
                YAML_WRITER.writeValue(out, items);
            }
            break;
        }
    }

    private static class Column<T>
    {
        private final String name;
        private final Function<T, String> accessor;

        private Column(String name, Function<T, String> accessor)
        {
            this.name = name;
            this.accessor = accessor;
        }
    }
}
