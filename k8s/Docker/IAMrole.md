1. Identify/Create an IAM role for the k8s serviceAccount to run the `duck` pod.
2. Identify the Arn for the OIDC provider to populate the `Federated` `Principal` for [kube-iam-trust-relationship.json](kube-iam-trust-relationship.json).

````
oidc_id=$(aws eks describe-cluster --name <clusterName> --query "cluster.identity.oidc.issuer" --output text | cut -d '/' -f 5)
aws iam list-open-id-connect-providers | grep $oidc_id
````

