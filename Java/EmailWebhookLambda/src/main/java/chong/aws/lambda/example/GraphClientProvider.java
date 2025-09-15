package chong.aws.lambda.example;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.authentication.TokenCredentialAuthProvider;
import com.microsoft.graph.requests.GraphServiceClient;
import okhttp3.Request;

import java.util.Arrays;

public class GraphClientProvider {
    private static GraphServiceClient<Request> graphClient;

    public static synchronized GraphServiceClient<Request> getClient() {
        if (graphClient == null) {
            String tenantId = System.getenv("AZURE_TENANT_ID");
            String clientId = System.getenv("AZURE_CLIENT_ID");
            String clientSecret = System.getenv("AZURE_CLIENT_SECRET");

            ClientSecretCredential credential = new ClientSecretCredentialBuilder()
                .tenantId(tenantId)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build();

            TokenCredentialAuthProvider authProvider = 
                new TokenCredentialAuthProvider(
                  Arrays.asList("https://graph.microsoft.com/.default"), 
                  credential);

            graphClient = GraphServiceClient.builder()
                .authenticationProvider(authProvider)
                .buildClient();
        }
        return graphClient;
    }
}
