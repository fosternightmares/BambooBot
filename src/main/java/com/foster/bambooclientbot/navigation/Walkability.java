package com.foster.bambooclientbot.navigation;

public enum Walkability {
    WALKABLE("walkable"),
    BLOCKED("blocked");

    private final String label;

    Walkability(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
