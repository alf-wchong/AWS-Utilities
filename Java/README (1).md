# Email Webhook Lambda

This repository contains a Java-based AWS Lambda function that integrates with Microsoft 365 via Microsoft Graph webhooks to retrieve emails in near real-time and store them in a proprietary content management system (Doxis).

## Table of Contents

- [Key Features](#key-features)
- [Prerequisites](#prerequisites)
- [Setup](#setup)
  - [Azure AD App Registration](#azure-ad-app-registration)
  - [AWS Infrastructure](#aws-infrastructure)
- [Configuration](#configuration)
- [Building the Project](#building-the-project)
- [Deployment](#deployment)
- [Usage](#usage)
- [Frequently Asked Questions](#frequently-asked-questions)
- [License](#license)

## Key Features

- Automatic creation and renewal of Microsoft Graph subscriptions for new email notifications
- Webhook handler for Graph validation and notification processing
- Retrieval of full email content using Microsoft Graph SDK
- Integration point for `storeEmailInDoxis()` to push emails into Doxis ECM
- Standalone Maven project packaged as a single fat JAR

## Prerequisites

### Azure

- Azure AD Tenant
- Azure AD App Registration with:
  - **Mail.Read** and **Mail.ReadWrite** application permissions
  - **admin consent** granted
- Application credentials:
  - **Tenant ID**
  - **Client (Application) ID**
  - **Client Secret**

### AWS

- AWS account with permissions to create:
  - Lambda function
  - API Gateway HTTP API
  - IAM Role for Lambda with `AWSLambdaBasicExecutionRole` and access to environment variables
  - (Optional) DynamoDB or Parameter Store for subscription metadata storage

### Local

- Java 11+ JDK
- Maven 3.6+
- (Optional) AWS SAM CLI or Serverless Framework

## Setup

### Azure AD App Registration

1. Navigate to **Azure Portal** > **Azure Active Directory** > **App registrations**.
2. Click **New registration** and provide a name.
3. Under **API permissions**, add:
   - **Microsoft Graph** > **Application permissions**:
     - `Mail.Read`
     - `Mail.ReadWrite`
4. Click **Grant admin consent**.
5. Under **Certificates & secrets**, create a new **Client secret**.
6. Note the **Tenant ID**, **Client ID**, and **Client Secret**.

### AWS Infrastructure

1. **IAM Role**
   - Create a role `EmailWebhookLambdaRole` with:
     - **AWSLambdaBasicExecutionRole** policy
     - (Optional) policies for DynamoDB or Parameter Store
2. **API Gateway HTTP API**
   - Create a new HTTP API.
   - Add a route **POST /notifications**.
   - Integrate with the Lambda function (created later).
   - (Optional) Add a custom header for webhook validation.

## Configuration

Set the following environment variables for the Lambda function:

- `AZURE_TENANT_ID` – Azure AD tenant ID
- `AZURE_CLIENT_ID` – Azure AD application ID
- `AZURE_CLIENT_SECRET` – Azure AD client secret
- `WEBHOOK_URL` – API Gateway endpoint URL (`https://.../notifications`)
- `CLIENT_STATE` – Shared secret for clientState validation

## Building the Project

```bash
git clone https://github.com/your-org/email-webhook-lambda.git
cd email-webhook-lambda
mvn clean package
```

The output `target/email-webhook-lambda-1.0.0.jar` is a shaded JAR containing all dependencies.

## Deployment

### AWS Console

1. Upload the JAR to a new Lambda function using the `EmailWebhookLambdaRole`.
2. Configure the handler: `com.example.email.EmailWebhookHandler::handleRequest`.
3. Set the environment variables as listed in [Configuration](#configuration).
4. On API Gateway, deploy the HTTP API to a stage and note the invoke URL.

### Using AWS SAM (Optional)

```yaml
AWSTemplateFormatVersion: '2010-09-09'
Transform: AWS::Serverless-2016-10-31
Resources:
  EmailWebhookFunction:
    Type: AWS::Serverless::Function
    Properties:
      Handler: com.example.email.EmailWebhookHandler::handleRequest
      Runtime: java11
      Role: ARN_OF_EmailWebhookLambdaRole
      Environment:
        Variables:
          AZURE_TENANT_ID: !Ref AzureTenantId
          AZURE_CLIENT_ID: !Ref AzureClientId
          AZURE_CLIENT_SECRET: !Ref AzureClientSecret
          WEBHOOK_URL: !Sub "https://${ApiGateway}.execute-api.${AWS::Region}.amazonaws.com/notifications"
          CLIENT_STATE: !Ref ClientState
      Events:
        NotifyRoute:
          Type: HttpApi
          Properties:
            ApiId: !Ref HttpApi
            Path: /notifications
            Method: post
```

Deploy:
```bash
sam deploy --guided
```

## Usage

1. Ensure the Azure AD app registration is configured.
2. Deploy the Lambda and API Gateway.
3. On cold start, the Lambda creates a Graph subscription for `/me/mailFolders('Inbox')/messages`.
4. When a new email arrives, Graph sends a POST to `/notifications`.
5. The Lambda validates the `clientState`, retrieves the full `Message`, and calls `storeEmailInDoxis()`.

## Frequently Asked Questions

**Q:** _How often does the subscription renew?_

**A:** Currently every cold start; recommended to track expiration and renew programmatically before expiry.

**Q:** _Can I filter specific messages?_

**A:** Yes, modify `SubscriptionManager.RESOURCE` with OData filters (e.g., `?$filter=from/emailAddress/address eq 'noreply@example.com'`).

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
