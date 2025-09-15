package chong.aws.lambda.example;

import com.microsoft.graph.models.Subscription;
import com.microsoft.graph.requests.GraphServiceClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.OffsetDateTime;
import java.util.UUID;

public class SubscriptionManager {
    private static final Logger logger = LogManager.getLogger(SubscriptionManager.class);
    private static final String NOTIFY_URL = System.getenv("WEBHOOK_URL");
    private static final String RESOURCE = "/me/mailFolders('Inbox')/messages";
    private static final int EXPIRY_MINUTES = 60; // Renew every hour

    public static void ensureSubscription() {
        try {
            OffsetDateTime expiration = OffsetDateTime.now().plusMinutes(EXPIRY_MINUTES);

            Subscription subscription = new Subscription();
            subscription.changeType = "created";
            subscription.notificationUrl = NOTIFY_URL;
            subscription.resource = RESOURCE;
            subscription.expirationDateTime = expiration;
            subscription.clientState = UUID.randomUUID().toString();

            GraphServiceClient<?> client = GraphClientProvider.getClient();
            Subscription created = client.subscriptions()
                .buildRequest()
                .post(subscription);
            logger.info("Created Graph subscription: " + created.id);
        } catch (Exception ex) {
            logger.error("Failed to create subscription", ex);
        }
    }
}