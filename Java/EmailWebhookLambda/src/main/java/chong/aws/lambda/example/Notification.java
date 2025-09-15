package chong.aws.lambda.example.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class Notification {
    public List<Value> value;

    public static class Value {
        public String subscriptionId;
        public String clientState;
        public String resource;
        @JsonProperty("resourceData")
        public ResourceData resourceData;
    }

    public static class ResourceData {
        public String id;
        @JsonProperty("@odata.etag")
        public String odataEtag;
        public String createdDateTime;
        public String lastModifiedDateTime;
    }
}