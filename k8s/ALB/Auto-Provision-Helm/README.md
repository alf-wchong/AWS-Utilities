# ALB Cerebro Helm Chart

This Helm chart deploys Cerebro connected to Amazon OpenSearch Serverless with AWS ALB ingress.

## Prerequisites

Before installing this Helm chart, ensure you have the following components installed and configured:

### 1. AWS Load Balancer Controller
The AWS Load Balancer Controller must be installed in your Kubernetes cluster to automatically provision ALBs.

```bash
# Add the EKS charts repository
helm repo add eks https://aws.github.io/eks-charts
helm repo update

# Install AWS Load Balancer Controller
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=<your-cluster-name> \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller
```

### 2. IAM Permissions and Service Account
Create the required IAM policy and service account for the Load Balancer Controller:

```bash
# Download the IAM policy document
curl -o iam_policy.json https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/v2.7.2/docs/install/iam_policy.json

# Create the IAM policy
aws iam create-policy \
    --policy-name AWSLoadBalancerControllerIAMPolicy \
    --policy-document file://iam_policy.json

# Create IAM service account with the policy attached
eksctl create iamserviceaccount \
  --cluster=<your-cluster-name> \
  --namespace=kube-system \
  --name=aws-load-balancer-controller \
  --attach-policy-arn=arn:aws:iam::<your-account-id>:policy/AWSLoadBalancerControllerIAMPolicy \
  --override-existing-serviceaccounts \
  --approve
```

### 3. AWS ACM Certificate
Create an SSL certificate in AWS Certificate Manager for your domain:

```bash
# Request a certificate for your domain
aws acm request-certificate \
  --domain-name claremont.books.com \
  --validation-method DNS \
  --region <your-region>
```

### 4. Amazon OpenSearch Serverless Collection
Ensure you have an OpenSearch Serverless collection created and accessible from your Kubernetes cluster.

### 5. Kubernetes TLS Secret (Optional)
If using TLS secrets in addition to ACM certificates:

```bash
kubectl create secret tls cerebro-tls \
  --cert=path/to/cert.crt \
  --key=path/to/cert.key
```

## Installation

### 1. Update Configuration Values
Before installing, update the values in `values.yaml`:

```yaml
opensearch:
  endpoint: "search-your-collection-xyz.aoss.us-west-2.amazonaws.com"
  region: "us-west-2"

ingress:
  certificateArn: "arn:aws:acm:us-west-2:123456789012:certificate/abcd1234-..."
  tlsSecretName: "cerebro-tls"  # Optional if using ACM only
```

### 2. Deploy the Chart
Install the Helm chart in your desired namespace:

```bash
# Create namespace (optional)
kubectl create namespace cerebro

# Install the chart
helm install cerebro . --namespace cerebro

# Or install with custom values
helm install cerebro . --namespace cerebro --values custom-values.yaml
```

### 3. Verify Deployment
Check that all resources are created and the ALB is provisioned:

```bash
# Check pods
kubectl get pods -n cerebro

# Check ingress
kubectl get ingress -n cerebro

# Check ALB creation (may take 2-3 minutes)
kubectl describe ingress cerebro -n cerebro
```

## Configuration

The following table lists the configurable parameters:

| Parameter | Description | Default | Required |
|-----------|-------------|---------|----------|
| `replicaCount` | Number of replicas | `1` | No |
| `image.repository` | Cerebro image repository | `lmenezes/cerebro` | No |
| `image.tag` | Cerebro image tag | `0.8.1` | No |
| `image.pullPolicy` | Image pull policy | `IfNotPresent` | No |
| `service.type` | Kubernetes service type | `ClusterIP` | No |
| `service.port` | Service port | `9000` | No |
| `opensearch.endpoint` | OpenSearch Serverless endpoint | `<placeholder>` | **Yes** |
| `opensearch.region` | AWS region | `<placeholder>` | **Yes** |
| `ingress.enabled` | Enable ingress | `true` | No |
| `ingress.hostname` | Domain name | `claremont.books.com` | **Yes** |
| `ingress.certificateArn` | ACM certificate ARN | `<placeholder>` | **Yes** |
| `ingress.tlsSecretName` | Kubernetes TLS secret name | `<placeholder>` | No |
| `resources` | Pod resource limits/requests | `{}` | No |
| `nodeSelector` | Node selector labels | `{}` | No |
| `tolerations` | Pod tolerations | `[]` | No |
| `affinity` | Pod affinity rules | `{}` | No |

## Troubleshooting

### ALB Not Created
If the Application Load Balancer is not being created:

1. Check if AWS Load Balancer Controller is running:
```bash
kubectl get pods -n kube-system -l app.kubernetes.io/name=aws-load-balancer-controller
```

2. Check controller logs:
```bash
kubectl logs -n kube-system deployment/aws-load-balancer-controller
```

3. Verify IAM permissions are correctly attached to the service account.

### DNS Resolution Issues
Ensure your domain `claremont.books.com` is properly configured to point to the ALB:

```bash
# Get ALB DNS name
kubectl get ingress cerebro -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'

# Create CNAME record: claremont.books.com -> ALB DNS name
```

### OpenSearch Connection Issues
If Cerebro cannot connect to OpenSearch Serverless:

1. Verify the endpoint URL is correct and accessible
2. Check network policies and security groups
3. Ensure proper authentication is configured for OpenSearch Serverless

## Uninstalling

To remove the chart and all its resources:

```bash
# Delete the Helm release
helm uninstall cerebro --namespace cerebro

# Delete the namespace (optional)
kubectl delete namespace cerebro
```

Note: The ALB will be automatically deleted when the Ingress resource is removed.

## External Dependencies Summary

This chart requires the following external components to be configured outside of Helm:

- ✅ AWS Load Balancer Controller (with IAM permissions)
- ✅ AWS ACM Certificate for your domain
- ✅ Amazon OpenSearch Serverless Collection
- ✅ Proper DNS configuration for your domain
- ✅ Network connectivity between Kubernetes cluster and OpenSearch
- ✅ EKS cluster with appropriate node groups and networking
