package com.foster.bambooclientbot.state;

import java.util.Locale;

public record GotoTarget(double x, double y, double z) {
    public String format() {
        return formatCoordinate(x) + "," + formatCoordinate(y) + "," + formatCoordinate(z);
    }

    public String formatForChat() {
        return formatCoordinate(x) + " " + formatCoordinate(y) + " " + formatCoordinate(z);
    }

    private static String formatCoordinate(double coordinate) {
        if (coordinate == Math.rint(coordinate)) {
            return Long.toString(Math.round(coordinate));
        }

        return String.format(Locale.ROOT, "%.2f", coordinate);
    }
}
