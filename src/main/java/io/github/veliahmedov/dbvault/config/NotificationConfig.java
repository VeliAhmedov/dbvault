package io.github.veliahmedov.dbvault.config;
// notification related config data
public record NotificationConfig(
        String webhook,         // destination URL (${WEBHOOK_URL})
        String notifyOn         // when notification should be sent (all(default) -> both success and fail, failure ->fail only)
) {
}
