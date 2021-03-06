package picocli;

import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.rules.TestRule;
import picocli.CommandLine.Command;
import picocli.CommandLine.IExitCodeGenerator;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.ParseResult;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Tests #454 repeatable subcommands.
 */
public class RepeatableSubcommandsTest {
    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    static class AbstractCommand implements Callable<Integer> {
        static Map<String, Integer> exitCodes = new HashMap<String, Integer>();
        boolean executed;
        public Integer call() {
            executed = true;
            return exitCode();
        }
        int exitCode() {
            Integer result = exitCodes.get(getClass().getSimpleName());
            return result == null ? 0 : result;
        }
    }

    @Command(name="A",
            subcommandsRepeatable = true,
            subcommands = {B.class, C.class, D.class})
    static class A extends AbstractCommand {
        @Option(names = "-x") String x;
        static AtomicInteger total = new AtomicInteger();
        static AtomicInteger count = new AtomicInteger();
        public A() {
            count.incrementAndGet();
            A.total.incrementAndGet();
        }
    }

    @Command(name="B",
            subcommandsRepeatable = true,
            subcommands = {E.class, F.class, G.class})
    static class B extends AbstractCommand {
        @Option(names = "-x") String x;
        static AtomicInteger count = new AtomicInteger();
        public B() {
            count.incrementAndGet();
            A.total.incrementAndGet();
        }
    }

    @Command(name="C")
    static class C extends AbstractCommand {
        @Option(names = "-x") String x;
        static AtomicInteger count = new AtomicInteger();
        public C() {
            count.incrementAndGet();
            A.total.incrementAndGet();
        }
    }

    @Command(name="D")
    static class D extends AbstractCommand {
        @Option(names = "-x") String x;
        static AtomicInteger count = new AtomicInteger();
        public D() {
            count.incrementAndGet();
            A.total.incrementAndGet();
        }
    }

    @Command(name="E")
    static class E extends AbstractCommand {
        @Option(names = "-x") String x;
        static AtomicInteger count = new AtomicInteger();
        public E() {
            count.incrementAndGet();
            A.total.incrementAndGet();
        }
    }

    @Command(name="F")
    static class F extends AbstractCommand {
        static AtomicInteger count = new AtomicInteger();
        public F() {
            count.incrementAndGet();
            A.total.incrementAndGet();
        }
    }

    @Command(name="G")
    static class G extends AbstractCommand {
        static AtomicInteger count = new AtomicInteger();
        public G() {
            count.incrementAndGet();
            A.total.incrementAndGet();
        }
    }

    @Test
    public void testParseResult() {
        //TestUtil.setTraceLevel("DEBUG");
        CommandLine cl = new CommandLine(new A());
        String[] args = "B B C D B E F G E E F F".split(" ");
        ParseResult parseResult = cl.parseArgs(args);
        StringWriter sw = new StringWriter();
        print("", parseResult, new PrintWriter(sw));
        String expected = String.format("" +
                "A%n" +
                "  B%n" +
                "  B%n" +
                "  C%n" +
                "  D%n" +
                "  B%n" +
                "    E%n" +
                "    F%n" +
                "    G%n" +
                "    E%n" +
                "    E%n" +
                "    F%n" +
                "    F%n");
        assertEquals(expected, sw.toString());

        List<CommandLine> commandLines = parseResult.asCommandLineList();
        List<String> expectedNames = new ArrayList<String>(Arrays.asList(args));
        expectedNames.add(0, "A");
        for (int i = 0; i < commandLines.size(); i++) {
            assertEquals("Command name at " + i, expectedNames.get(i), commandLines.get(i).getCommandName());
        }
    }

    private void print(String indent, ParseResult parseResult, PrintWriter pw) {
        pw.println(indent + parseResult.commandSpec().name());
        for (ParseResult sub : parseResult.subcommands()) {
            print(indent + "  ", sub, pw);
        }
    }

    private void resetCounters() {
        A.total.set(0);
        A.count.set(0);
        B.count.set(0);
        C.count.set(0);
        D.count.set(0);
        E.count.set(0);
        F.count.set(0);
        G.count.set(0);
    }

    @Test
    public void testCommandLazyInstantiation() {
        resetCounters();

        CommandLine cl = new CommandLine(A.class);
        assertEquals(0, A.count.get());
        assertEquals(0, B.count.get());
        assertEquals(0, C.count.get());
        assertEquals(0, D.count.get());
        assertEquals(0, E.count.get());
        assertEquals(0, F.count.get());
        assertEquals(0, G.count.get());
        assertEquals(0, A.total.get());

        resetCounters();
        cl.parseArgs();
        assertEquals(1, A.count.get());
        assertEquals(0, B.count.get());
        assertEquals(0, C.count.get());
        assertEquals(0, D.count.get());
        assertEquals(0, E.count.get());
        assertEquals(0, F.count.get());
        assertEquals(0, G.count.get());
        assertEquals(1, A.total.get());

        resetCounters();
        cl.parseArgs("-x=y");
        assertEquals(0, A.count.get()); // previously created instance is reused
        assertEquals(0, B.count.get());
        assertEquals(0, C.count.get());
        assertEquals(0, D.count.get());
        assertEquals(0, E.count.get());
        assertEquals(0, F.count.get());
        assertEquals(0, G.count.get());
        assertEquals(0, A.total.get());

        resetCounters();
        cl.parseArgs("-x=y", "B");
        assertEquals(0, A.count.get());
        assertEquals(1, B.count.get());
        assertEquals(0, C.count.get());
        assertEquals(0, D.count.get());
        assertEquals(0, E.count.get());
        assertEquals(0, F.count.get());
        assertEquals(0, G.count.get());
        assertEquals(1, A.total.get());

        resetCounters();
        cl.parseArgs("-x=y", "B", "-x=y");
        assertEquals(0, A.count.get());
        assertEquals(0, B.count.get()); // previously created instance is reused
        assertEquals(0, C.count.get());
        assertEquals(0, D.count.get());
        assertEquals(0, E.count.get());
        assertEquals(0, F.count.get());
        assertEquals(0, G.count.get());
        assertEquals(0, A.total.get());

        resetCounters();
        cl.parseArgs("C");
        assertEquals(0, A.count.get());
        assertEquals(0, B.count.get());
        assertEquals(1, C.count.get());
        assertEquals(0, D.count.get());
        assertEquals(0, E.count.get());
        assertEquals(0, F.count.get());
        assertEquals(0, G.count.get());
        assertEquals(1, A.total.get());

        resetCounters();
        cl.parseArgs("C", "-x=y");
        assertEquals(0, A.count.get());
        assertEquals(0, B.count.get());
        assertEquals(0, C.count.get()); // previously created instance is reused
        assertEquals(0, D.count.get());
        assertEquals(0, E.count.get());
        assertEquals(0, F.count.get());
        assertEquals(0, G.count.get());
        assertEquals(0, A.total.get());

        resetCounters();
        cl.parseArgs("B", "E");
        assertEquals(0, A.count.get());
        assertEquals(0, B.count.get()); // previously created instance is reused
        assertEquals(0, C.count.get());
        assertEquals(0, D.count.get());
        assertEquals(1, E.count.get());
        assertEquals(0, F.count.get());
        assertEquals(0, G.count.get());
        assertEquals(1, A.total.get());

        resetCounters();
        cl.parseArgs("B", "E", "-x=y");
        assertEquals(0, A.count.get());
        assertEquals(0, B.count.get()); // previously created instance is reused
        assertEquals(0, C.count.get());
        assertEquals(0, D.count.get());
        assertEquals(0, E.count.get()); // previously created instance is reused
        assertEquals(0, F.count.get());
        assertEquals(0, G.count.get());
        assertEquals(0, A.total.get());

        resetCounters();
        cl.parseArgs("B", "-x=y", "E", "-x=y");
        assertEquals(0, A.count.get());
        assertEquals(0, B.count.get()); // instance is reused
        assertEquals(0, C.count.get());
        assertEquals(0, D.count.get());
        assertEquals(0, E.count.get()); // instance is reused
        assertEquals(0, F.count.get());
        assertEquals(0, G.count.get());
        assertEquals(0, A.total.get());

        resetCounters();
        cl.parseArgs("-x=y", "B", "-x=y", "E", "-x=y");
        assertEquals(0, A.count.get()); // instance is reused
        assertEquals(0, B.count.get()); // instance is reused
        assertEquals(0, C.count.get());
        assertEquals(0, D.count.get());
        assertEquals(0, E.count.get()); // instance is reused
        assertEquals(0, F.count.get());
        assertEquals(0, G.count.get());
        assertEquals(0, A.total.get());
    }

    @Test
    public void testCommandLazyInstantiation2() {
        resetCounters();

        CommandLine cl = new CommandLine(A.class);
        assertEquals(0, A.count.get());
        assertEquals(0, B.count.get());
        assertEquals(0, C.count.get());
        assertEquals(0, D.count.get());
        assertEquals(0, E.count.get());
        assertEquals(0, F.count.get());
        assertEquals(0, G.count.get());
        assertEquals(0, A.total.get());

        resetCounters();
        cl = new CommandLine(A.class);
        cl.parseArgs();
        assertEquals(1, A.count.get());
        assertEquals(0, B.count.get());
        assertEquals(0, C.count.get());
        assertEquals(0, D.count.get());
        assertEquals(0, E.count.get());
        assertEquals(0, F.count.get());
        assertEquals(0, G.count.get());
        assertEquals(1, A.total.get());

        resetCounters();
        cl = new CommandLine(A.class);
        cl.parseArgs("-x=y");
        assertEquals(1, A.count.get());
        assertEquals(0, B.count.get());
        assertEquals(0, C.count.get());
        assertEquals(0, D.count.get());
        assertEquals(0, E.count.get());
        assertEquals(0, F.count.get());
        assertEquals(0, G.count.get());
        assertEquals(1, A.total.get());

        resetCounters();
        cl = new CommandLine(A.class);
        cl.parseArgs("-x=y", "B");
        assertEquals(1, A.count.get());
        assertEquals(1, B.count.get());
        assertEquals(0, C.count.get());
        assertEquals(0, D.count.get());
        assertEquals(0, E.count.get());
        assertEquals(0, F.count.get());
        assertEquals(0, G.count.get());
        assertEquals(2, A.total.get());

        resetCounters();
        cl = new CommandLine(A.class);
        cl.parseArgs("-x=y", "B", "-x=y");
        assertEquals(1, A.count.get());
        assertEquals(1, B.count.get());
        assertEquals(0, C.count.get());
        assertEquals(0, D.count.get());
        assertEquals(0, E.count.get());
        assertEquals(0, F.count.get());
        assertEquals(0, G.count.get());
        assertEquals(2, A.total.get());

        resetCounters();
        cl = new CommandLine(A.class);
        cl.parseArgs("C");
        assertEquals(1, A.count.get());
        assertEquals(0, B.count.get());
        assertEquals(1, C.count.get());
        assertEquals(0, D.count.get());
        assertEquals(0, E.count.get());
        assertEquals(0, F.count.get());
        assertEquals(0, G.count.get());
        assertEquals(2, A.total.get());

        resetCounters();
        cl = new CommandLine(A.class);
        cl.parseArgs("C", "-x=y");
        assertEquals(1, A.count.get());
        assertEquals(0, B.count.get());
        assertEquals(1, C.count.get());
        assertEquals(0, D.count.get());
        assertEquals(0, E.count.get());
        assertEquals(0, F.count.get());
        assertEquals(0, G.count.get());
        assertEquals(2, A.total.get());

        resetCounters();
        cl = new CommandLine(A.class);
        cl.parseArgs("B", "E");
        assertEquals(1, A.count.get());
        assertEquals(1, B.count.get());
        assertEquals(0, C.count.get());
        assertEquals(0, D.count.get());
        assertEquals(1, E.count.get());
        assertEquals(0, F.count.get());
        assertEquals(0, G.count.get());
        assertEquals(3, A.total.get());

        resetCounters();
        cl = new CommandLine(A.class);
        cl.parseArgs("B", "E", "-x=y");
        assertEquals(1, A.count.get());
        assertEquals(1, B.count.get());
        assertEquals(0, C.count.get());
        assertEquals(0, D.count.get());
        assertEquals(1, E.count.get());
        assertEquals(0, F.count.get());
        assertEquals(0, G.count.get());
        assertEquals(3, A.total.get());

        resetCounters();
        cl = new CommandLine(A.class);
        cl.parseArgs("B", "-x=y", "E", "-x=y");
        assertEquals(1, A.count.get());
        assertEquals(1, B.count.get());
        assertEquals(0, C.count.get());
        assertEquals(0, D.count.get());
        assertEquals(1, E.count.get());
        assertEquals(0, F.count.get());
        assertEquals(0, G.count.get());
        assertEquals(3, A.total.get());

        resetCounters();
        cl = new CommandLine(A.class);
        cl.parseArgs("-x=y", "B", "-x=y", "E", "-x=y");
        assertEquals(1, A.count.get());
        assertEquals(1, B.count.get());
        assertEquals(0, C.count.get());
        assertEquals(0, D.count.get());
        assertEquals(1, E.count.get());
        assertEquals(0, F.count.get());
        assertEquals(0, G.count.get());
        assertEquals(3, A.total.get());
    }

    @Test
    public void testParseResultAsCommandLineList() {
        CommandLine cl = new CommandLine(new A());
        String[] args = "B B C D B E F G E E F F".split(" ");
        ParseResult parseResult = cl.parseArgs(args);
        List<CommandLine> commandLines = parseResult.asCommandLineList();
        List<String> expectedNames = new ArrayList<String>(Arrays.asList(args));
        expectedNames.add(0, "A");
        for (int i = 0; i < commandLines.size(); i++) {
            assertEquals("Command name at " + i, expectedNames.get(i), commandLines.get(i).getCommandName());
        }
    }

    @Test
    public void testExecution() {
        CommandLine cl = new CommandLine(new A());
        cl.execute("B B C D B E F G E E F F".split(" "));

        List<CommandLine> commandLines = cl.getParseResult().asCommandLineList();
        boolean[] executed = new boolean[] {
                false, // A
                false, // B
                false, // B
                false, // C
                false, // D
                false, // B
                true, // E
                true, // F
                true, // G
                true, // E
                true, // E
                true, // F
                true, // F
        };
        assertEquals(executed.length, commandLines.size());
        for (int i = 0; i < executed.length; i++) {
            AbstractCommand ac = commandLines.get(i).getCommand();
            assertEquals("[" + i + "]: " + ac, executed[i], ac.executed);
        }
    }

    @Test
    public void testExecutionRunAll() {
        CommandLine cl = new CommandLine(new A());
        cl.setExecutionStrategy(new CommandLine.RunAll());
        cl.execute("B B C D B E F G E E F F".split(" "));

        List<CommandLine> commandLines = cl.getParseResult().asCommandLineList();
        boolean[] executed = new boolean[] {
                true, // A
                true, // B
                true, // B
                true, // C
                true, // D
                true, // B
                true, // E
                true, // F
                true, // G
                true, // E
                true, // E
                true, // F
                true, // F
        };
        assertEquals(executed.length, commandLines.size());
        for (int i = 0; i < executed.length; i++) {
            AbstractCommand ac = commandLines.get(i).getCommand();
            assertEquals("[" + i + "]: " + ac, executed[i], ac.executed);
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testLegacyExecution() {
        CommandLine cl = new CommandLine(new A());
        cl.parseWithHandler(new CommandLine.RunLast(), "B B C D B E F G E E F F".split(" "));

        List<CommandLine> commandLines = cl.getParseResult().asCommandLineList();
        boolean[] executed = new boolean[] {
                false, // A
                false, // B
                false, // B
                false, // C
                false, // D
                false, // B
                true, // E
                true, // F
                true, // G
                true, // E
                true, // E
                true, // F
                true, // F
        };
        assertEquals(executed.length, commandLines.size());
        for (int i = 0; i < executed.length; i++) {
            AbstractCommand ac = commandLines.get(i).getCommand();
            assertEquals("[" + i + "]: " + ac, executed[i], ac.executed);
        }
    }

    @Test
    public void testExitCodesPositive() {
        AbstractCommand.exitCodes.put("A", 20);
        AbstractCommand.exitCodes.put("B", 7);
        AbstractCommand.exitCodes.put("C", 10);
        AbstractCommand.exitCodes.put("E", 6);
        AbstractCommand.exitCodes.put("F", 5);
        AbstractCommand.exitCodes.put("G", 4);

        CommandLine cl = new CommandLine(new A());
        int exitCode = cl.execute("B B C D B E F G E E F F".split(" "));
        assertEquals(6, exitCode);
    }

    @Test
    public void testExitCodesNegative() {
        AbstractCommand.exitCodes.put("A", -20);
        AbstractCommand.exitCodes.put("B", -7);
        AbstractCommand.exitCodes.put("C", -10);
        AbstractCommand.exitCodes.put("E", -6);
        AbstractCommand.exitCodes.put("F", -5);
        AbstractCommand.exitCodes.put("G", -4);

        CommandLine cl = new CommandLine(new A());
        int exitCode = cl.execute("B B C D B E F G E E F F".split(" "));
        assertEquals(-6, exitCode);
    }

    @Command(name = "print", subcommands = FileCommand.class, subcommandsRepeatable = true)
    static class Print implements Runnable {
        enum Paper {A1, A2, A3, A4, A5, B1, B2, B3, B4, B5}
        @Option(names = "--paper") Paper paper;

        public void run() {
            //System.out.println("Print (paper=" + paper + ")");
        }
    }
    @Command(name = "file")
    static class FileCommand implements Callable<Integer> {
        @Parameters(index = "0", paramLabel = "FILE")
        File file;

        @Option(names = "--count") int count = 1;

        enum Rotate {left, right}
        @Option(names = "--rotate") Rotate rotate;

        public Integer call() {
            //System.out.printf("File (file=%s, count=%d, rotate=%s)%n", file, count, rotate);
            return count;
        }
    }

    @Test
    public void testOriginalUseCase() {
        Print print = new Print();
        CommandLine cmd = new CommandLine(print);
        int exitCode = cmd.execute((
                "--paper A4" +
                        " file A.pdf" +
                        " file B.pdf --count 3" +
                        " file C.pdf --count 2 --rotate left" +
                        " file D.pdf" +
                        " file E.pdf --rotate right").split(" "));
        assertEquals(3, exitCode);

        ParseResult parseResult = cmd.getParseResult();
        assertEquals(Print.Paper.A4, print.paper);
        assertSame(Print.Paper.A4, parseResult.matchedOptionValue("--paper", null));

        assertEquals(5, parseResult.subcommands().size());

        Object[][] expecteds = new Object[][] {
                new Object[] {new File("A.pdf"), 1, null},
                new Object[] {new File("B.pdf"), 3, null},
                new Object[] {new File("C.pdf"), 2, FileCommand.Rotate.left},
                new Object[] {new File("D.pdf"), 1, null},
                new Object[] {new File("E.pdf"), 1, FileCommand.Rotate.right},
        };
        int i = 0;
        for (Object[] expected : expecteds) {
            ParseResult pr = parseResult.subcommands().get(i++);
            FileCommand file = (FileCommand) pr.commandSpec().userObject();
            assertEquals(expected[0], file.file);
            assertEquals(expected[1], file.count);
            assertEquals(expected[2], file.rotate);
        }
    }

    @Command(name = "parent", subcommands = MixinTestSubCommand.class, subcommandsRepeatable = true)
    static class MixinTestParentCommand {
        static int methodSubInvocationCount;
        @Command
        int methodSub(@Mixin MyMixin myMixin) {
            return ++methodSubInvocationCount * 100 + myMixin.x;
        }
    }

    @Command(name = "sub")
    static class MixinTestSubCommand implements Runnable, IExitCodeGenerator {
        static int invocationCount;
        @Mixin MyMixin myMixin;
        @ParentCommand MixinTestParentCommand parent;

        public void run() { ++invocationCount; }
        public int getExitCode() { return invocationCount * myMixin.x; }
    }

    static class MyMixin {
        @ParentCommand MixinTestParentCommand parent;
        @Option(names = "-x", defaultValue = "26") int x;
    }
    @Test
    public void testMixinsAndExitCodeGenerator() {
        MixinTestParentCommand.methodSubInvocationCount = 0;
        MixinTestSubCommand.invocationCount = 0;

        MixinTestParentCommand parent = new MixinTestParentCommand();
        CommandLine cl = new CommandLine(parent);
        int exitCode = cl.execute("sub -x3 sub sub".split(" "));
        assertEquals(3 * 26, exitCode);
        assertEquals(3, MixinTestSubCommand.invocationCount);
        assertEquals(0, MixinTestParentCommand.methodSubInvocationCount);

        MixinTestParentCommand.methodSubInvocationCount = 0;
        MixinTestSubCommand.invocationCount = 0;
        exitCode = cl.execute("sub -x3 sub -x 4 sub -x5 sub -x=2".split(" "));
        assertEquals(4 * 5, exitCode);
        assertEquals(4, MixinTestSubCommand.invocationCount);
        assertEquals(0, MixinTestParentCommand.methodSubInvocationCount);

        MixinTestParentCommand.methodSubInvocationCount = 0;
        MixinTestSubCommand.invocationCount = 0;
        exitCode = cl.execute("sub -x3 sub -x 4 methodSub -x2 sub -x5 methodSub -x3 sub -x=2".split(" "));
        assertEquals(2 * 100 + 3, exitCode);
        assertEquals(4, MixinTestSubCommand.invocationCount);
        assertEquals(2, MixinTestParentCommand.methodSubInvocationCount);
    }

    @Test
    public void testParentOrderInRepeatableCommands() {
        MixinTestParentCommand.methodSubInvocationCount = 0;
        MixinTestSubCommand.invocationCount = 0;

        MixinTestParentCommand parent = new MixinTestParentCommand();
        CommandLine cl = new CommandLine(parent);
        cl.execute("sub -x3 sub sub".split(" "));
        assertEquals(3, cl.getParseResult().subcommands().size());
        for (ParseResult subPR : cl.getParseResult().subcommands()) {
            MixinTestSubCommand sub = subPR.commandSpec().commandLine().getCommand();
            assertSame(parent, sub.parent);
        }
    }

    @Command(subcommandsRepeatable = true)
    static class MultivalueTop implements Runnable {

        @Command
        int sub1(@Option(names = "-x", arity = "*") List<String> x) {
            return x.size();
        }
        @Command
        int sub2(@Option(names = "-y", arity = "*") List<String> y) {
            return y.size();
        }
        public void run() { }
    }
    @Test
    public void testMultivalueOptions() {
        int exitCode = new CommandLine(new MultivalueTop()).execute("sub1 -x1 -x2 -x3 sub2 -y1 -y2 -y3 -y4".split(" "));
        assertEquals(4, exitCode);
    }

    @Test
    public void testCommandSpec_SubcommandsRepeatable() {
        class Positional {
            @Parameters(index = "0") String first;
        }
        CommandLine cmd = new CommandLine(new Positional());
        CommandLine.Model.CommandSpec spec = cmd.getCommandSpec();
        assertFalse("orig", spec.subcommandsRepeatable());
        spec.subcommandsRepeatable(true);
        assertTrue("after", spec.subcommandsRepeatable());
    }
}
