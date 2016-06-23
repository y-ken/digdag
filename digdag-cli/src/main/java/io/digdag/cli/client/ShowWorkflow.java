package io.digdag.cli.client;

import io.digdag.cli.SystemExitException;
import io.digdag.client.DigdagClient;
import io.digdag.client.api.RestProject;
import io.digdag.client.api.RestWorkflowDefinition;
import io.digdag.core.Version;

import javax.ws.rs.NotFoundException;

import java.io.PrintStream;
import java.util.List;

import static io.digdag.cli.SystemExitException.systemExit;

public class ShowWorkflow
    extends ClientCommand
{
    public ShowWorkflow(Version version, PrintStream out, PrintStream err)
    {
        super(version, out, err);
    }

    @Override
    public void mainWithClientException()
        throws Exception
    {
        switch (args.size()) {
        case 0:
            showWorkflows(null);
            break;
        case 1:
            showWorkflows(args.get(0));
            break;
        case 2:
            showWorkflowDetails(args.get(0), args.get(1));
            break;
        default:
            throw usage(null);
        }
    }

    public SystemExitException usage(String error)
    {
        err.println("Usage: digdag workflows [project-name] [name]");
        showCommonOptions();
        return systemExit(error);
    }

    private void showWorkflows(String projName)
        throws Exception
    {
        DigdagClient client = buildClient();

        TablePrinter table = new TablePrinter(out);
        table.row("PROJECT", "PROJECT ID", "WORKFLOW", "REVISION");

        if (projName != null) {
            RestProject proj = client.getProject(projName);
            List<RestWorkflowDefinition> defs = client.getWorkflowDefinitions(proj.getId());
            printProject(table, proj, defs);
        }
        else {
            for (RestProject proj : client.getProjects()) {
                List<RestWorkflowDefinition> defs;
                try {
                    defs = client.getWorkflowDefinitions(proj.getId());
                }
                catch (NotFoundException ex) {
                    continue;
                }
                printProject(table, proj, defs);
            }
        }
        table.print();
        out.println();
        out.flush();
        err.println("Use `digdag workflows <project-name> <name>` to show details.");
    }

    private void printProject(TablePrinter tablePrinter, RestProject proj, List<RestWorkflowDefinition> defs)
    {
        for (RestWorkflowDefinition def : defs) {
            tablePrinter.row(proj.getName(), Integer.toString(proj.getId()), def.getName(), def.getRevision());
        }
    }

    private void showWorkflowDetails(String projName, String defName)
        throws Exception
    {
        DigdagClient client = buildClient();

        if (projName != null) {
            RestProject proj = client.getProject(projName);
            RestWorkflowDefinition def = client.getWorkflowDefinition(proj.getId(), defName);
            String yaml = yamlMapper().toYaml(def.getConfig());
            ln("%s", yaml);
        }
        else {
            for (RestProject proj : client.getProjects()) {
                try {
                    RestWorkflowDefinition def = client.getWorkflowDefinition(proj.getId(), defName);
                    String yaml = yamlMapper().toYaml(def.getConfig());
                    ln("%s", yaml);
                    return;
                }
                catch (NotFoundException ex) {
                }
            }
            throw systemExit("Workflow definition '" + defName + "' does not exist.");
        }
    }
}
