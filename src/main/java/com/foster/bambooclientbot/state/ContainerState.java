package com.foster.bambooclientbot.state;

public record ContainerState(boolean open, String type, String title) {
    public static ContainerState closed() {
        return new ContainerState(false, "", "");
    }

    public String format() {
        if (!open) {
            return "containerOpen=false";
        }

        return "containerOpen=true containerType=" + type + " containerTitle=" + title;
    }
}
