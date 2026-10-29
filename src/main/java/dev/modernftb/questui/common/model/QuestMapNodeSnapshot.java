package dev.modernftb.questui.common.model;

public record QuestMapNodeSnapshot(long id, long questId, double x, double y, double width, double height, double iconScale, String shape, boolean link, boolean visible) {
    public QuestMapNodeSnapshot {
        width = Math.max(1.0, width);
        height = Math.max(1.0, height);
        iconScale = Math.max(0.1, iconScale);
        shape = shape == null || shape.isBlank() ? "default" : shape;
    }
}

