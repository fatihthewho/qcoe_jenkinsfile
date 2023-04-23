/*
 * AWS commands
 *
 * @author Phill Krall
 */

// http = load "DevOps\\http\\http.groovy"

def startAndWaitInstance(instanceID, region = 'us-east-1') {
    echo "Starting instance ${instanceID}"
    run("aws ec2 start-instances --instance-id ${instanceID} --region ${region}")
    echo "Wait for instance ${instanceID} to start"
    run("aws ec2 wait instance-status-ok --instance-ids ${instanceID} --region ${region}")
}

def stopAndWaitInstance(instanceID, region = 'us-east-1') {
    echo "Stoping instance ${instanceID}"
    run("aws ec2 stop-instances --instance-id ${instanceID} --region ${region}")
    echo "Wait for instance ${instanceID} to stop"
    run("aws ec2 wait instance-stopped --instance-ids ${instanceID} --region ${region}")
}

def getInstanceID(ip, region = 'us-east-1') {
    echo "getting instacne id for ${ip}"
    def temp = run("""aws ec2 describe-instances --filter Name=private-ip-address,Values=${ip} --query "Reservations[].Instances[].InstanceId" --output text""")
    String[] id = temp.split("\n")
    return id[id.length - 1].trim()
}

def run(command) {
    echo "${command}"
    def result = ''
    if (System.properties['os.name'].toLowerCase().contains('windows')) {
        result = bat(script: "${command}", returnStdout: true).trim()
    } else {
        result = sh(script: "${command}", returnStdout: true).trim()
    }
    return result.replace(command, "")
}

return this
