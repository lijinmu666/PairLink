package com.lijinmu.pairlink;

public final class SummaryWindowTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        long hour = 60 * 60 * 1000L;
        long start = 100 * hour;
        check(!SummaryWindow.due(start, start + 15 * 60000L - 1, 15), "Do not notify before the chosen interval");
        check(SummaryWindow.due(start, start + 15 * 60000L, 15), "Notify at the interval boundary");
        check(!SummaryWindow.due(start, start - hour, 15), "Clock rollback must not send a summary");
        check(SummaryWindow.from(start, start + 3 * hour) == start, "Delayed jobs include the entire unsummarized window");
        check(SummaryWindow.from(start, start + 30 * hour) == start + 6 * hour, "Never claim records older than retention");
        check(SummaryWindow.from(start + hour, start) == start, "Future cursor must not produce a negative window");
        check("1 小时 7 分钟".equals(SummaryWindow.elapsedLabel(start, start + 67 * 60000L)), "Notification uses elapsed duration, not configured duration");
        check("24 小时".equals(SummaryWindow.elapsedLabel(start, start + 24 * hour)), "Daily duration");
        check(!SummaryWindow.validInterval(14) && SummaryWindow.interval(14) == 60, "Invalid stored intervals have a supported fallback");
        check(SummaryWindow.validInterval(15) && SummaryWindow.validInterval(1440), "Supported interval extremes");
        System.out.println("SummaryWindow tests passed");
    }
}
