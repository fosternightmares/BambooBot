package com.foster.bambooclientbot.state;

public record GotoResult(GotoTarget target, String result, String reason) {
    public String format() {
        if (target == null) {
            return "lastGoto=none";
        }

        if (reason == null || reason.isBlank()) {
            return "lastGoto=" + target.format() + " result=" + result;
        }

        return "lastGoto=" + target.format() + " result=" + result + " reason=" + reason;
    }

    public String targetLabel() {
        if (target == null) {
            return "none";
        }

        return target.format();
    }
}
