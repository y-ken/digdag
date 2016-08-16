package io.digdag.cli;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.MissingCommandException;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.collect.ImmutableMap;
import io.digdag.cli.client.Archive;
import io.digdag.cli.client.Backfill;
import io.digdag.cli.client.Delete;
import io.digdag.cli.client.Kill;
import io.digdag.cli.client.Push;
import io.digdag.cli.client.Reschedule;
import io.digdag.cli.client.Retry;
import io.digdag.cli.client.ShowAttempt;
import io.digdag.cli.client.ShowAttempts;
import io.digdag.cli.client.ShowLog;
import io.digdag.cli.client.ShowSchedule;
import io.digdag.cli.client.ShowSession;
import io.digdag.cli.client.ShowTask;
import io.digdag.cli.client.ShowWorkflow;
import io.digdag.cli.client.Start;
import io.digdag.cli.client.Upload;
import io.digdag.cli.client.Version;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import static io.digdag.cli.SystemExitException.systemExit;

import static io.digdag.cli.ConfigUtil.defaultConfigPath;
import static io.digdag.core.agent.OperatorManager.formatExceptionMessage;
import static io.digdag.core.Version.buildVersion;

public class Main
{
    private static final String PROGRAM_NAME = "digdag";

    private final io.digdag.core.Version version;
    private final PrintStream out;
    private final PrintStream err;
    private final Map<String, String> env;

    public Main(io.digdag.core.Version version, PrintStream out, PrintStream err) {
        this(version, out, err, ImmutableMap.copyOf(System.getenv()));
    }

    public Main(io.digdag.core.Version version, PrintStream out, PrintStream err, Map<String, String> env)
    {
        this.version = version;
        this.out = out;
        this.err = err;
        this.env = env;
    }

    public static class MainOptions
    {
        @Parameter(names = {"-help", "--help"}, help = true, hidden = true)
        boolean help;
    }

    public static void main(String... args)
    {
        int code = new Main(buildVersion(), System.out, System.err).cli(args);
        if (code != 0) {
            System.exit(code);
        }
    }

    public int cli(String... args)
    {
        if (args.length == 1 && args[0].equals("--version")) {
            out.println(version.version());
            return 0;
        }
        err.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z").format(new Date()) + ": Digdag v" + version);

        boolean verbose = false;

        MainOptions mainOpts = new MainOptions();
        JCommander jc = new JCommander(mainOpts);
        jc.setProgramName(PROGRAM_NAME);

        jc.addCommand("init", new Init(env, out, err), "new");
        jc.addCommand("run", new Run(env, out, err), "r");
        jc.addCommand("check", new Check(env, out, err), "c");
        jc.addCommand("scheduler", new Sched(version, env, out, err), "sched");

        jc.addCommand("server", new Server(version, env, out, err));

        jc.addCommand("push", new Push(version, env, out, err));
        jc.addCommand("archive", new Archive(env, out, err));
        jc.addCommand("upload", new Upload(version, env, out, err));

        jc.addCommand("workflow", new ShowWorkflow(version, env, out, err), "workflows");
        jc.addCommand("start", new Start(version, env, out, err));
        jc.addCommand("retry", new Retry(version, env, out, err));
        jc.addCommand("session", new ShowSession(version, env, out, err), "sessions");
        jc.addCommand("attempts", new ShowAttempts(version, env, out, err));
        jc.addCommand("attempt", new ShowAttempt(version, env, out, err));
        jc.addCommand("reschedule", new Reschedule(version, env, out, err));
        jc.addCommand("backfill", new Backfill(version, env, out, err));
        jc.addCommand("log", new ShowLog(version, env, out, err), "logs");
        jc.addCommand("kill", new Kill(version, env, out, err));
        jc.addCommand("task", new ShowTask(version, env, out, err), "tasks");
        jc.addCommand("schedule", new ShowSchedule(version, env, out, err), "schedules");
        jc.addCommand("delete", new Delete(env, version, out, err));
        jc.addCommand("version", new Version(version, env, out, err), "version");

        jc.addCommand("selfupdate", new SelfUpdate(env, out, err));

        try {
            try {
                jc.parse(args);
            }
            catch (MissingCommandException ex) {
                throw usage(err, "available commands are: "+jc.getCommands().keySet());
            }
            catch (ParameterException ex) {
                if (getParsedCommand(jc) == null) {
                    // go to Run.asImplicit section
                }
                else {
                    throw ex;
                }
            }

            if (mainOpts.help) {
                throw usage(err, null);
            }

            Command command = getParsedCommand(jc);
            if (command == null) {
                throw usage(err, null);
            }

            verbose = processCommonOptions(err, command);

            command.main();
            return 0;
        }
        catch (ParameterException ex) {
            err.println("error: " + ex.getMessage());
            return 1;
        }
        catch (SystemExitException ex) {
            if (ex.getMessage() != null) {
                err.println("error: " + ex.getMessage());
            }
            return ex.getCode();
        }
        catch (Exception ex) {
            String message = formatExceptionMessage(ex);
            if (message.trim().isEmpty()) {
                // prevent silent crash
                ex.printStackTrace(err);
            }
            else {
                err.println("error: " + message);
                if (verbose) {
                    ex.printStackTrace(err);
                }
            }
            return 1;
        }
    }

    private static Command getParsedCommand(JCommander jc)
    {
        String commandName = jc.getParsedCommand();
        if (commandName == null) {
            return null;
        }

        return (Command) jc.getCommands().get(commandName).getObjects().get(0);
    }

    private static boolean processCommonOptions(PrintStream err, Command command)
            throws SystemExitException
    {
        if (command.help) {
            throw command.usage(null);
        }

        boolean verbose;

        switch (command.logLevel) {
        case "error":
        case "warn":
        case "info":
            verbose = false;
            break;
        case "debug":
        case "trace":
            verbose = true;
            break;
        default:
            throw usage(err, "Unknown log level '"+command.logLevel+"'");
        }

        configureLogging(command.logLevel, command.logPath);

        for (Map.Entry<String, String> pair : command.systemProperties.entrySet()) {
            System.setProperty(pair.getKey(), pair.getValue());
        }

        return verbose;
    }

    private static void configureLogging(String level, String logPath)
    {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        context.reset();

        // logback uses system property to embed variables in XML file
        Level lv = Level.toLevel(level.toUpperCase(), Level.DEBUG);
        System.setProperty("digdag.log.level", lv.toString());

        String name;
        if (logPath.equals("-")) {
            if (System.console() != null) {
                name = "/digdag/cli/logback-color.xml";
            } else {
                name = "/digdag/cli/logback-console.xml";
            }
        } else {
            System.setProperty("digdag.log.path", logPath);
            name = "/digdag/cli/logback-file.xml";
        }
        try {
            configurator.doConfigure(Main.class.getResource(name));
        } catch (JoranException ex) {
            throw new RuntimeException(ex);
        }
    }

    // called also by Run
    static SystemExitException usage(PrintStream err, String error)
    {
        err.println("Usage: digdag <command> [options...]");
        err.println("  Local-mode commands:");
        err.println("    new <path>                       create a new workflow project");
        err.println("    r[un] <workflow.dig>             run a workflow");
        err.println("    c[heck]                          show workflow definitions");
        err.println("    sched[uler]                      run a scheduler server");
        err.println("    selfupdate                       update digdag to the latest version");
        err.println("");
        err.println("  Server-mode commands:");
        err.println("    server                           start digdag server");
        err.println("");
        err.println("  Client-mode commands:");
        err.println("    push <project-name>              create and upload a new revision");
        err.println("    start <project-name> <name>      start a new session attempt of a workflow");
        err.println("    retry <attempt-id>               retry a session");
        err.println("    kill <attempt-id>                kill a running session attempt");
        err.println("    backfill <project-name> <name>   start sessions of a schedule for past times");
        err.println("    reschedule                       skip sessions of a schedule to a future time");
        err.println("    log <attempt-id>                 show logs of a session attempt");
        err.println("    workflows [project-name] [name]  show registered workflow definitions");
        err.println("    schedules                        show registered schedules");
        err.println("    sessions                         show sessions for all workflows");
        err.println("    sessions <project-name>          show sessions for all workflows in a project");
        err.println("    sessions <project-name> <name>   show sessions for a workflow");
        err.println("    session  <session-id>            show a single session");
        err.println("    attempts                         show attempts for all sessions");
        err.println("    attempts <session-id>            show attempts for a session");
        err.println("    attempt  <attempt-id>            show a single attempt");
        err.println("    tasks <attempt-id>               show tasks of a session attempt");
        err.println("    delete <project-name>            delete a project");
        err.println("    version                          show client and server version");
        err.println("");
        err.println("  Options:");
        showCommonOptions(err);
        if (error == null) {
            err.println("Use `<command> --help` to see detailed usage of a command.");
            return systemExit(null);
        }
        else {
            return systemExit(error);
        }
    }

    public static void showCommonOptions(PrintStream err)
    {
        err.println("    -L, --log PATH                   output log messages to a file (default: -)");
        err.println("    -l, --log-level LEVEL            log level (error, warn, info, debug or trace)");
        err.println("    -X KEY=VALUE                     add a performance system config");
        err.println("    -c, --config PATH.properties     Configuration file (default: " + defaultConfigPath() + ")");
        err.println("");
    }
}
