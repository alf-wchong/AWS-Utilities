package chong.aws.lambda.example;

import chong.aws.lambda.example.model.Notification;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.requests.GraphServiceClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class EmailWebhookHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static final Logger logger = LogManager.getLogger(EmailWebhookHandler.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String CLIENT_STATE = System.getenv("CLIENT_STATE");

    static {
        SubscriptionManager.ensureSubscription();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            String body = request.getBody();
            // 1. Validation handshake
            if (request.getQueryStringParameters() != null && request.getQueryStringParameters().containsKey("validationToken")) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(200)
                        .withBody(request.getQueryStringParameters().get("validationToken"));
            }

            // 2. Process notifications
            Notification notification = mapper.readValue(body, Notification.class);
            List<Notification.Value> items = notification.value;
            GraphServiceClient<?> client = GraphClientProvider.getClient();

            for (Notification.Value item : items) {
                if (!CLIENT_STATE.equals(item.clientState)) {
                    logger.warn("ClientState mismatch, ignoring notification");
                    continue;
                }
                // 3. Retrieve full message
                Message message = client
                        .me()
                        .messages(item.resourceData.id)
                        .buildRequest()
                        .select("subject,from,body,bodyPreview,receivedDateTime")
                        .get();

                // 4. Push to proprietary CMS
                storeEmailInDoxis(message);
            }

            return new APIGatewayProxyResponseEvent().withStatusCode(202);
        } catch (Exception ex) {
            logger.error("Error handling webhook", ex);
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("Error: " + ex.getMessage());
        }
    }

    private void storeEmailInDoxis(Message message) {
        // Implement your CMS integration here
    }
}