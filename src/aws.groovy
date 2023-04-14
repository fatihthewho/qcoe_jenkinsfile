/*
 * AWS commands
 *
 * @author Phill Krall
 */

// http = load "DevOps\\http\\http.groovy"

def startAndWaitInstance(instanceID, region='us-east-1') {
    echo "Starting instance ${instanceID}"
    run("aws ec2 start-instances --instance-id ${instanceID} --region ${region}")
    echo "Wait for instance ${instanceID} to start"
    run("aws ec2 wait instance-status-ok --instance-ids ${instanceID} --region ${region}")
}

def stopAndWaitInstance(instanceID, region='us-east-1') {
    echo "Stoping instance ${instanceID}"
    run("aws ec2 stop-instances --instance-id ${instanceID} --region ${region}")
    echo "Wait for instance ${instanceID} to stop"
    run("aws ec2 wait instance-stopped --instance-ids ${instanceID} --region ${region}")
}
def getInstanceID(ip, region='us-east-1') {
    echo "getting instacne id for ${ip}"
    def temp = run("""aws ec2 describe-instances --filter Name=private-ip-address,Values=${ip} --query "Reservations[].Instances[].InstanceId" --output text""")
    String[] id = temp.split("\n");
      return id[id.length-1]

}



def run(command) {
    echo "${command}"
    def result = ''
    if (System.properties['os.name'].toLowerCase().contains('windows')) {
        result = bat(script: "${command}", returnStdout: true).trim()
    } else {
        result = sh(script: "${command}", returnStdout: true).trim()
    }
    return result.replace(command,"")
}
/*

*/
/* Upsert S3 File request *//*

def upsert(name, json_path, metadata, bucket, region, mode) {
    def url = 'https://mvynwo8yib.execute-api.us-east-1.amazonaws.com/dev/2020.02'
    def headers = [:]
    headers['name'] = name
    headers['filename'] = json_path
    headers['metadata'] = metadata
    headers['bucket'] = bucket
    headers['region'] = region
    headers['mode'] = mode
    http.sendLegacyPostRequest(url, headers)
}

*/
/* Run an AWS command and trim the excess spaces and braces from the output *//*

def command(cmd, post=null, list=false) {
    cmd = """${cmd.trim()} | grep '"' | cut -d '"' -f 2${post == null ? '' : (' | ' + post)}"""
    echo "Run AWS command: ${cmd}"
    def result = run("${cmd}")
    result = list ? (result == '' ? [] : result.split('\n')) : result
    echo "Results: ${result}"
    return result
}

*/
/* Get the maximum number of a type of resource allowed per VPC *//*

def getLimit(resource) {
    def result = 0
    switch (resource.toLowerCase()) {
        case 'eks':
        case 'oidc':
            result = 100
            break
        case 'elb':
            result = command("""aws elb describe-account-limits --query 'Limits[?Name==`classic-load-balancers`].Max'""") as int
            break
        case 'db':
        case 'database':
            result = sh script: """aws service-quotas list-service-quotas --service-code rds --query 'Quotas[?QuotaName==`DB clusters`].Value' | grep '[[:digit:]]'""", returnStdout: true
            result = (int) (result as double)
            break
        default:
            throw new IllegalArgumentException('Unsupported resource type: ' + resource)
    }
    return result
}

*/
/* Get the current number of resources of a given type on the current VPC *//*

def getCount(region, resource) {
    def result = 0
    switch (resource.toLowerCase()) {
        case 'eks':
            result = command("""aws eks list-clusters --no-paginate --query 'clusters[*]' --region ${region}""", 'wc -l')
            break
        case 'elb':
            result = command("""aws elb describe-load-balancers --query 'LoadBalancerDescriptions[*].LoadBalancerName' --region ${region}""", 'wc -l')
            break
        case 'db':
        case 'database':
            result = command("""aws rds describe-db-clusters --query 'DBClusters[*].DBClusterIdentifier' --region ${region}""", 'wc -l')
            break
        case 'oidc':
            result = command("""aws iam list-open-id-connect-providers --query 'OpenIDConnectProviderList[*].Arn'""", 'wc -l')
            break
        default:
            throw new IllegalArgumentException('Unsupported resource type: ' + resource)
    }
    return result as int
}

*/
/* Add the given tags to the given resource *//*

def tag(region, resource, resource_id, tags=[:], extra=null) {
    if (tags.isEmpty()) {
        echo 'No tags given. Returning immediately.'
        error('Failed to tag item')
    }
    switch (resource.toLowerCase()) {
        case 'eks':
            def cmdTags = ''
            tags.each { cmdTags += ",${it.key}=${it.value}" }
            sh "aws eks tag-resource --resource-arn ${resource_id} --tags \"${cmdTags.substring(1)}\" --region ${region}"
            break
        case 'ec2':
            def cmdTags = ''
            tags.each { cmdTags += " Key=${it.key},Value=${it.value.replaceAll(' ','\\\\ ')}" }
            sh "aws ec2 create-tags --resources ${resource_id} --tags ${cmdTags.substring(1)} --region ${region}"
            break
        case 'elb':
            def cmdTags = ''
            tags.each { cmdTags += ",${it.key}=${it.value}" }
            def arn = ''
            resource_id.split(' ').each {
                arn += "\"arn:aws:elasticloadbalancing:${region}:${getAwsAccount()}:loadbalancer/${it}\" "
            }
            sh "aws resourcegroupstaggingapi tag-resources --resource-arn-list ${arn} --tags \"${cmdTags.substring(1)}\" --region ${region}"
            break
        case 's3':
            if (extra == null || extra == '') {
                echo 'Usage: tag(s3, resource_id, tags, s3_path)'
                error('Missing S3 object path')
            }
            def cmdTags = '{\\"TagSet\\": ['
            newTags = []
            tags.each { newTags.add("{ \\\"Key\\\": \\\"${it.key}\\\", \\\"Value\\\": \\\"${it.value}\\\"}") }
            cmdTags += newTags.join(',');
            cmdTags += ']}'
            run( "aws s3api put-object-tagging --bucket ${resource_id} --key ${extra} --tagging \"${cmdTags}\"")
            break;
        default:
            throw new IllegalArgumentException('Unsupported resource type: ' + resource)
    }
}

*/
/* Get Route53 zone ID from hostname *//*

def getHostZone(hostname) {
    return command("aws route53 list-hosted-zones-by-name --query 'HostedZones[?Name==`${hostname}.`].Id'", "cut -d '/' -f 3")
}

*/
/* Get load balancer zone ID from endpoint URL *//*

def getLoadBalancerZone(region, endpoint) {
    return command("aws elb describe-load-balancers --query 'LoadBalancerDescriptions[?DNSName==`${endpoint}`].CanonicalHostedZoneNameID' --region ${region}")
}

*/
/* Get AWS region *//*

def getRegion() {
    return sh(script: 'aws configure get region', returnStdout: true).trim()
}

*/
/* Change AWS region *//*

def setRegion(region) {
    run("aws configure set region \"${region}\"")
}

*/
/* List all available VPC's in the current region *//*

def getVpcs(region) {
    return command("aws ec2 describe-vpcs --query 'Vpcs[?State==`available`].VpcId' --region ${region}", null, true)
}

*/
/* Get VPC's that have been tagged with a certain name *//*

def getVpcsByName(region, name) {
    return command("""
        aws resourcegroupstaggingapi get-resources --resource-type-filters ec2:vpc --region ${region} --query \
                'ResourceTagMappingList[?Tags[?Key==`Name` && contains(Value,`${name}`)]].ResourceARN'
    """, "cut -d '/' -f 2", true)
}

*/
/* Get VPC's that are in the given CIDR block *//*

def getVpcsByCidr(region, cidr, ignore_default=true) {
    def skip_default = ignore_default ? '!IsDefault && ' : ''
    return command("aws ec2 describe-vpcs --query 'Vpcs[?${skip_default}starts_with(CidrBlock,`${cidr}`)].VpcId' --region ${region}", null, true)
}

*/
/* List all subnets on the given VPC *//*

def getSubnets(region, vpc) {
    def public_subnets = command("""
        aws ec2 describe-route-tables --query 'RouteTables[?VpcId==`${vpc}` && Tags[?Value==`PublicRouteTable`]].Associations[].SubnetId' --region ${region}
    """)
    def subnets = [:]
    subnets['public'] = []
    subnets['private'] = []
    def temp
    int i = 0
    command("""
        aws ec2 describe-subnets --query 'sort_by(Subnets,&AvailabilityZone)[?VpcId==`${vpc}`].[SubnetId,AvailabilityZone,CidrBlock]' --region ${region}
    """, null, true).each {
        switch (i % 3) {
            case 0:
                temp = [:]
                temp['id'] = "${it}"
                break
            case 1:
                temp['zone'] = "${it}"
                break
            case 2:
                temp['cidr'] = "${it}"
                def privacy = public_subnets.contains("${temp['id']}") ? 'public' : 'private'
                if (subnets[privacy] == [] || subnets[privacy][-1]['zone'] != temp['zone']) {
                    subnets[privacy].add(temp)
                } else {
                    subnets[privacy][-1] = temp
                }
                break
        }
        i++
    }
    return subnets
}

*/
/* List all available RDS subnet groups for the given VPC *//*

def getSubnetGroups(region, vpc) {
    return command("""aws rds describe-db-subnet-groups --query 'DBSubnetGroups[?VpcId==`${vpc}`].DBSubnetGroupName' --region ${region}""", null, true)
}

*/
/* Get instance ID for the EC2 instance with the given name *//*

def getInstanceIdByName(region, name) {
    return command("""
        aws ec2 describe-instances --query 'Reservations[*].Instances[?Tags[?Key==`Name` && Value==`${name}-linux-Node`].Value].InstanceId' --region ${region}
    """)
}

*/
/* Get default security group for the given VPC *//*

def getDefaultSecurityGroup(region, vpc) {
    return command("""aws ec2 describe-security-groups --query 'SecurityGroups[?VpcId==`${vpc}`].GroupId' --filters 'Name=group-name,Values=default' --region ${region}""")
}

*/
/* Get CIDR subnet block for the given VPC *//*

def getCidrBlock(region, vpc) {
    return command("""aws ec2 describe-vpcs --query 'Vpcs[?VpcId==`${vpc}`].CidrBlockAssociationSet[0].CidrBlock' --region ${region}""")
}

def getAwsAccount() {
    return command("""aws sts get-caller-identity --query 'Account'""")
}

def getRoute53HostZone(mustContain) {
    def filter = "Name==`${mustContain}.`"
    def zone = command("aws route53 list-hosted-zones --query 'HostedZones[?${filter}].Name'")
    */
/* IF no exact match THEN get the first zone whose name includes the "mustContain" value *//*

    if (zone == null || zone == '') {
        filter = "contains(Name,`${mustContain}`)"
        zone = command("aws route53 list-hosted-zones --query 'HostedZones[?${filter}].Name'", null, true)
        if (zone == null || zone == []) {
            error("Failed to find any Route53 zones matching name: \"${mustContain}\"")
        }
        zone = zone[0]
    }
    return zone.substring(0, zone.length() - 1)
}

*/
/* Get security group by name *//*

def getSecurityGroup(region, name) {
    return command("""aws ec2 describe-security-groups --query 'SecurityGroups[?GroupName==`${name}`].GroupId' --region ${region}""")
}

*/
/* Create a security group that allows all traffic *//*

def createOpenWorldSecurityGroup(region, vpc, prefix, tags=[:]) {
    sh """aws ec2 create-security-group --group-name ${prefix}OpenToTheWorld --description "Allow all traffic" --vpc-id ${vpc} --region ${region}"""
    def sg = getSecurityGroup(region, "${prefix}OpenToTheWorld")
    if (sg == null || sg == '') {
        error('Failed to create open world security group.')
    }
    sh """
        aws ec2 authorize-security-group-ingress --region ${region} --group-id ${sg} --ip-permissions \
                '[{"IpProtocol":"-1","PrefixListIds":[],"IpRanges":[{"CidrIp":"0.0.0.0/0"}],"UserIdGroupPairs":[],"Ipv6Ranges":[{"CidrIpv6":"::/0"}]}]'
    """
    tag(region, 'ec2', sg, tags)
    return sg
}

*/
/* Get EC2 instance status *//*

def getInstanceStatus(region, id) {
    return command("aws ec2 describe-instance-status --query 'InstanceStatuses[?InstanceId==`${id}`].InstanceState.Name' --region ${region}")
}


def getBuckets(filter) {
    return command("aws s3api list-buckets --query 'Buckets[].Name'", "grep ${filter}")
}

*/
/* Download a single S3 file *//*

def getS3File(bucket, key, output) {
    sh "aws s3 cp s3://${bucket}/${key} ${output}"
}

*/
/* Upload a single S3 file *//*

def pushS3File(bucket, key, file, kmsKey=null) {
    def kmsArgs = kmsKey == null ? '' : "--sse 'aws:kms' --sse-kms-key-id '${kmsKey}'"
    sh "aws s3 cp ${file} s3://${bucket}/${key} ${kmsArgs}"
}

*/
/* Download the full contents of a bucket *//*

def downloadS3Bucket(bucket, path) {
    sh "aws s3 sync s3://${bucket} ${path}"
}

*/
/* Update Load Balancer timeout from the default 60 seconds to the given value *//*

def setLoadBalancerTimeout(region, id, seconds) {
    sh """
        aws elb modify-load-balancer-attributes --region ${region} --load-balancer-name ${id} \
                --load-balancer-attributes "{\\"ConnectionSettings\\":{\\"IdleTimeout\\":${seconds}}}"
    """
}

*/
/* Compile database ARN from the ID *//*

def getDatabaseArn(region, id) {
    return "arn:aws:rds:${region}:${getAwsAccount()}:cluster:${id}"
}

*/
/* Get list of ARNs for policies with names matching the given value *//*

def getIamPolicyArns(name) {
    return command("aws iam list-policies --query 'Policies[?contains(Arn,`${name}`)].Arn'", null, true)
}

*/
/* Find IAM roles by partial name *//*

def getIamRoles(name) {
    return command("aws iam list-roles --query 'Roles[?contains(RoleName,`${name}`)].RoleName'", null, true)
}

*/
/* Check if a lambda function with the given name exists *//*

def lambdaExists(region, name) {
    return command("aws lambda list-functions --query 'Functions[?FunctionName==`${name}`].FunctionName' --region ${region}") != ''
}

*/
/* Check if a CloudWatch log group with the given name exists *//*

def logGroupExists(region, name) {
    return command("aws logs describe-log-groups --query 'logGroups[?contains(logGroupName,`${name}`)].logGroupName' --region ${region}", null, true) != []
}

*/
/* Delete an IAM role with the given name *//*

def deleteIamRole(name) {
    def policies = command("""aws iam list-attached-role-policies --role-name '${name}' --query 'AttachedPolicies[].PolicyArn'""", null, true)
    policies.each {
        sh "aws iam detach-role-policy --role-name '${name}' --policy-arn '${it}'"
    }
    sh "aws iam delete-role --role-name '${name}'"
}

*/
/* Delete an IAM policy with the given ARN *//*

def deleteIamPolicy(arn) {
    sh "aws iam delete-policy --policy-arn '${arn}'"
}

*/
/* Delete a lambda function with the given name *//*

def deleteLambda(region, name) {
    if (lambdaExists(region, name)) {
        sh "aws lambda delete-function --function-name '${name}' --region ${region}"
    } else {
        echo "Lambda '${name}' not found."
    }
}

*/
/* Delete a CloudWatch log group if it exists *//*

def deleteLogGroup(region, name) {
    if (logGroupExists(region, name)) {
        sh "aws logs delete-log-group --log-group-name '${name}' --region ${region}"
    }
}

*/
/* List all images in the given ECR repository *//*

def getEcrImages(region, repoName) {
    return command("""aws ecr list-images --repository-name ${repoName} --query 'imageIds[].imageTag' --region ${region}""", null, true)
}

*/
/* Create RDS database snapshot *//*

def snapshotDatabase(region, id) {
    def timestamp = new Date().format('yyyyMMdd', TimeZone.getTimeZone('America/New_York'))
    def current = command("""
        aws rds describe-db-cluster-snapshots --region ${region} --query 'sort_by(DBClusterSnapshots[?contains(DBClusterSnapshotIdentifier,`snap-${timestamp}-${id}`)],&DBClusterSnapshotIdentifier)[].DBClusterSnapshotIdentifier'
    """, "tail -n 1 | sed 's/snap-${timestamp}-${id}-//'", false)
    if (current == null || current == '') {
        sh """aws rds create-db-cluster-snapshot --db-cluster-identifier ${id} --db-cluster-snapshot-identifier snap-${timestamp}-${id}-1 --region ${region}"""
    } else {
        current = current as int
        sh """aws rds create-db-cluster-snapshot --db-cluster-identifier ${id} --db-cluster-snapshot-identifier snap-${timestamp}-${id}-${current + 1} --region ${region}"""
    }
}

*/
/* Get ARN for a secret with the given name *//*

def getSecretArn(region, name) {
    return command("""aws secretsmanager list-secrets --query 'SecretList[?Name==`${name.startsWith('db/') ? '' : 'db/'}${name}`].ARN' --region ${region}""")
}

*/
/* Set Map Public IP On Launch setting for the given subnet *//*

def setMapPublicIp(region, privacy, id) {
    def current = sh(script: "aws ec2 describe-subnets --query 'Subnets[?SubnetId==`${id}`].MapPublicIpOnLaunch' --region ${region} | grep 'true\\|false'", returnStdout: true).trim()
    echo "aws ec2 describe-subnets --query 'Subnets[?SubnetId==`${id}`].MapPublicIpOnLaunch' --region ${region} => ${current}"
    if (current == '') {
        error("Subnet not found: ${id}")
    }
    current = current.toBoolean()
    if (privacy == 'public' && !current) {
        echo "Enable Auto-assign Public IP for public subnet ${id}"
        sh "aws ec2 modify-subnet-attribute --subnet-id ${id} --map-public-ip-on-launch --region ${region}"
    } else if (privacy == 'private' && current) {
        echo "Disable Auto-assign Public IP for private subnet ${id}"
        sh "aws ec2 modify-subnet-attribute --subnet-id ${id} --no-map-public-ip-on-launch --region ${region}"
    }
}
*/

return this
