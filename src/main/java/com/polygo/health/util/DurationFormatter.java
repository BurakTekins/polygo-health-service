package com.polygo.health.util;

import java.time.Duration;

public final class DurationFormatter {

    private DurationFormatter() {}

    public static String fromMinutes(long totalMinutes) {
        if (totalMinutes < 60) {
            return totalMinutes + " minutes";
        }

        long totalHours = totalMinutes / 60;

        if (totalHours < 24) {
            return totalHours + " hours";
        }

        long days = totalHours / 24;
        long remainingHours = totalHours % 24;

        if (remainingHours == 0) {
            return days + " days";
        }

        return days + " days " + remainingHours + " hours";
    }

    public static String fromDuration(Duration duration) {
        return fromMinutes(duration.toMinutes());
    }
}
