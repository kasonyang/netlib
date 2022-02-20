package site.kason.netlib.util;

/**
 * @author KasonYang
 */
public class Logger {

    private enum LogLevel {

        NONE(0, "None"),
        ERROR(10, "Error"),
        WARN(20, "Warn"),
        INFO(30, "Info"),
        DEBUG(40, "Debug");

        private int value;

        private String name;

        LogLevel(int value, String name) {
            this.value = value;
            this.name = name;
        }

        public static LogLevel getByName(String name) {
            for (LogLevel level : values()) {
                if (level.name.toLowerCase().equals(name.toLowerCase())) {
                    return level;
                }
            }
            return NONE;
        }

    }


    private final static LogLevel LOG_LEVEL;

    static {
        String log = System.getProperty("site.kason.netlib.log.level", "error").toLowerCase();
        LOG_LEVEL = LogLevel.getByName(log);
    }

    public static Logger getLogger(String name) {
        return new Logger(name);
    }

    public static Logger getLogger(Class<?> clazz) {
        return getLogger(clazz.getName());
    }

    private String name;

    private Logger(String name) {
        this.name = name;
    }

    public void debug(String msg, Object... args) {
        log(LogLevel.DEBUG, msg, args);
    }

    public void info(String msg, Object... args) {
        log(LogLevel.INFO, msg, args);
    }

    public void warn(String msg, Object... args) {
        log(LogLevel.WARN, msg, args);
    }

    public void error(String msg, Object... args) {
        log(LogLevel.ERROR, msg, args);
    }

    public void error(Throwable ex) {
        if (LOG_LEVEL.value >= LogLevel.ERROR.value) {
            ex.printStackTrace(System.err);
        }
    }

    private void log(LogLevel level, String msg, Object... args) {
        if (LOG_LEVEL.value >= level.value) {
            System.err.println(String.format("%s: %s: ", level.name, name) + String.format(msg, args));
        }
    }

}
