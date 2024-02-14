#!/bin/bash
REPONAME=$1
REGION=$2
AWSString=$(aws ecr create-repository --region $REGION --repository-name $REPONAME --image-tag-mutability MUTABLE --image-scanning-configuration scanOnPush=false)
ImageUri=$(aws ecr describe-repositories --region $REGION --repository-names $REPONAME | jq -r .repositories[].repositoryUri)
echo $ImageUri
