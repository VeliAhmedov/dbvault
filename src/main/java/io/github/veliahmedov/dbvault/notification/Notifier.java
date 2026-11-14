package io.github.veliahmedov.dbvault.notification;

import io.github.veliahmedov.dbvault.config.NotificationConfig;
import io.github.veliahmedov.dbvault.exception.DbVaultException;
import java.net.URI;

//interface factory method, abstract for impls, it doesn't specify how notification delivered
// it is provided by webhook mainly (MockNotifier, FileNotifier and ConsoleNotifier is though upon on)
public interface Notifier {
    void send(boolean success, String message);

    // lambda impl of interface that does nothing, it is for operations that dones't need notification (list backups)
    Notifier NONE = (success, message) -> {};

    //static factory method, to send notification with config given
    static Notifier from(NotificationConfig config) {
        // if config missing, return NONE
        if (config == null || config.webhook() == null || config.webhook().isBlank()) return NONE;
        // If  webhook URL empty, return NONE
        String mode = config.notifyOn() == null || config.notifyOn().isBlank()
                ? "all" : config.notifyOn().trim().toLowerCase();
        // current notifyOn mode support all and failure if not any throw exception
        if (!mode.equals("all") && !mode.equals("failure"))
            throw new DbVaultException("notifications.notifyOn must be 'all' or 'failure'");

        // never print the URL in errors: it is a secret
        URI uri;        //uri of webhook, provide info and scheme
        try {
            uri = URI.create(config.webhook().trim());
        } catch (IllegalArgumentException e) {
            throw new DbVaultException("Invalid notifications.webhook URL");
        }
        //checks if it uses http or https, if none throw exception
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
            throw new DbVaultException("notifications.webhook must start with http:// or https://");

        return new WebhookNotifier(uri, mode.equals("failure"));
    }
}
