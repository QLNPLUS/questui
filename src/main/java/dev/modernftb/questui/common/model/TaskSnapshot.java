package dev.modernftb.questui.common.model;

public record TaskSnapshot(long id, String title, String typeId, String typeName, long progress, long maxProgress, boolean completed, boolean canClick) {
    public TaskSnapshot {
        title = title == null || title.isBlank() ? "Task" : title;
        typeId = typeId == null ? "" : typeId;
        typeName = typeName == null ? "" : typeName;
        progress = Math.max(0L, progress);
        maxProgress = Math.max(1L, maxProgress);
    }
}

