package com.foster.bambooclientbot.state;

public record DropResult(String itemId, String targetPlayer, int requestedCount, int droppedCount,
                         String result, String reason) {
    public String format() {
        String status = "lastDrop=" + compactItemId(itemId)
                + " target=" + targetPlayer
                + " requested=" + requestedCount
                + " dropped=" + droppedCount
                + " result=" + result;

        if (reason == null || reason.isBlank()) {
            return status;
        }

        return status + " reason=" + reason;
    }

    private String compactItemId(String itemId) {
        if (itemId.startsWith("minecraft:")) {
            return itemId.substring("minecraft:".length());
        }

        return itemId;
    }
}
