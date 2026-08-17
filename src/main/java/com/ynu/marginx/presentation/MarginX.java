package com.ynu.marginx.presentation;

import com.ynu.marginx.presentation.cli.MarginXCommand;
import java.util.Arrays;
import java.util.List;

/**
 * The single entry point, which decides whether this is a terminal run or a window.
 *
 * <p>No arguments, or {@code --gui}, opens the GUI; anything else is the command line, unchanged.
 * Double-clicking the installed application passes no arguments, which is exactly the case that
 * should open a window rather than print a usage message.
 *
 * <p>It deliberately does not extend {@code Application}. Launching a class that does, from a jar
 * where JavaFX sits on the class path rather than the module path, fails with "JavaFX runtime
 * components are missing"; going through a plain class avoids that check.
 */
public final class MarginX {

    private static final String GUI_FLAG = "--gui";

    private MarginX() {
    }

    public static void main(String[] args) {
        if (wantsGui(args)) {
            // Reflection keeps JavaFX off the path a headless CLI run has to load.
            startGui(withoutGuiFlag(args));
            return;
        }
        MarginXCommand.main(args);
    }

    static boolean wantsGui(String[] args) {
        return args.length == 0 || Arrays.asList(args).contains(GUI_FLAG);
    }

    static String[] withoutGuiFlag(String[] args) {
        List<String> remaining = Arrays.stream(args).filter(argument -> !GUI_FLAG.equals(argument)).toList();
        return remaining.toArray(String[]::new);
    }

    private static void startGui(String[] args) {
        try {
            Class<?> application = Class.forName("com.ynu.marginx.presentation.gui.MarginXFxApplication");
            application.getMethod("start", String[].class).invoke(null, (Object) args);
        } catch (ReflectiveOperationException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            System.err.println(" ERROR : cannot start the window: " + cause.getMessage());
            System.exit(1);
        }
    }
}
