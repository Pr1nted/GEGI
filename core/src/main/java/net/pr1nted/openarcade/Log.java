package net.pr1nted.openarcade;

/**
 * Logging for the shared core, which runs on every Minecraft from 1.12.2 (log4j, no
 * slf4j) to today (slf4j). Each version's mod points it at the game's own logger with
 * {@link #use(Sink)}; until then it writes to stderr. Messages take slf4j-style
 * {@code {}} placeholders.
 */
public final class Log {
    private Log() {}

    public interface Sink {
        void info(String message);

        void warn(String message, Throwable error);

        void error(String message, Throwable error);
    }

    private static volatile Sink sink = new Sink() {
        @Override
        public void info(String message) {
            System.err.println("[Open Arcade] " + message);
        }

        @Override
        public void warn(String message, Throwable error) {
            System.err.println("[Open Arcade] WARN " + message);
            if (error != null) error.printStackTrace();
        }

        @Override
        public void error(String message, Throwable error) {
            System.err.println("[Open Arcade] ERROR " + message);
            if (error != null) error.printStackTrace();
        }
    };

    public static void use(Sink newSink) {
        sink = newSink;
    }

    public static void info(String format, Object... args) {
        sink.info(format(format, args));
    }

    public static void warn(String format, Object... args) {
        sink.warn(format(format, args), trailingError(args));
    }

    public static void error(String format, Object... args) {
        sink.error(format(format, args), trailingError(args));
    }

    /** Like slf4j: a Throwable after the last placeholder's argument is the error, not text. */
    private static Throwable trailingError(Object[] args) {
        return args.length > 0 && args[args.length - 1] instanceof Throwable ? (Throwable) args[args.length - 1] : null;
    }

    static String format(String format, Object[] args) {
        StringBuilder out = new StringBuilder(format.length() + 32);
        int arg = 0;
        int from = 0;
        int at;
        while ((at = format.indexOf("{}", from)) >= 0) {
            out.append(format, from, at);
            out.append(arg < args.length ? String.valueOf(args[arg++]) : "{}");
            from = at + 2;
        }
        return out.append(format.substring(from)).toString();
    }
}
