package com.foster.bambooclientbot.state;

import java.util.List;

public record ContainerSnapshot(long timestamp, int slotCount, List<Entry> entries) {
    public int occupiedSlots() {
        return entries.size();
    }

    public record Entry(int slot, String itemId, int count) {
    }
}
