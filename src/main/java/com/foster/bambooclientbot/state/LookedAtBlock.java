package com.foster.bambooclientbot.state;

public record LookedAtBlock(String blockId, int x, int y, int z, String side, double distance, double hitX, double hitY, double hitZ) {
    public String format() {
        return "lookingAtBlock=" + blockId
                + " pos=" + x + "," + y + "," + z
                + " side=" + side
                + " distance=" + String.format(java.util.Locale.ROOT, "%.1f", distance);
    }
}
