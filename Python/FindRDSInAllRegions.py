import boto3

ec2_client = boto3.client('ec2')

# Retrieves all regions/endpoints that work with EC2
regions = ec2_client.describe_regions()

# Loop through each region
for region in regions['Regions']:

        # Create an RDS client for the region
        rds_client = boto3.client('rds', region_name=region['RegionName'])

        # List RDS instances
        response = rds_client.describe_db_instances()
        for db in response['DBInstances']:
            print(db['DBInstanceIdentifier']+" "+region['RegionName'])
