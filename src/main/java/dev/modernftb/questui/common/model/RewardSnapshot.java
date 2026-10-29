package dev.modernftb.questui.common.model;

public record RewardSnapshot(long id, String title, boolean claimed, boolean claimable) {
    public RewardSnapshot {
        title = title == null || title.isBlank() ? "Reward" : title;
    }
}

