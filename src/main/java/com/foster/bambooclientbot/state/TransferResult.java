package com.foster.bambooclientbot.state;

public record TransferResult(String operation, String itemId, int requestedCount, int movedCount,
                             String result, String reason) {
    public String format() {
        String status = "lastTransfer=" + operation
                + " " + compactItemId(itemId)
                + " requested=" + requestedCount
                + " moved=" + movedCount
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
