package com.voltyx.efw.client;

import com.voltyx.efw.EFW;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

/**
 * Drops log lines other mods write every frame. Bookling Gear's FancyMenu compat logs two lines per frame while the pause screen is
 * open ("Captured responsive FancyMenu runtime anchor ...", "Discarded mismatched FancyMenu anchor ..."): thousands of lines a
 * session, two thirds of latest.log. Everything else passes untouched.
 */
public final class LogSpamFilter extends AbstractFilter {
    private static final String[] PREFIXES = {
            "Captured responsive FancyMenu runtime anchor for ",
            "Discarded mismatched FancyMenu anchor for ",
    };

    private LogSpamFilter() {
        super(Filter.Result.DENY, Filter.Result.NEUTRAL);
    }

    public static void install() {
        try {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            ctx.getConfiguration().addFilter(new LogSpamFilter());
            ctx.updateLoggers();
        } catch (Throwable t) {
            EFW.LOGGER.warn("[Log] cannot install the spam filter: {}", t.toString());
        }
    }

    private static Filter.Result check(String msg) {
        if (msg != null) {
            for (String p : PREFIXES) {
                if (msg.startsWith(p)) {
                    return Filter.Result.DENY;
                }
            }
        }
        return Filter.Result.NEUTRAL;
    }

    @Override
    public Result filter(LogEvent event) {
        Message m = event.getMessage();
        return m == null ? Result.NEUTRAL : check(m.getFormat());
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String msg, Object... params) {
        return check(msg);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Message msg, Throwable t) {
        return msg == null ? Result.NEUTRAL : check(msg.getFormat());
    }
}
