import groovy.json.JsonSlurperClassic
import groovy.json.JsonOutput
EMAIL_INFO = "temp"

def checkoutRepo(url,branch){
	echo "checking out ${url} ${branch} "
	checkout([$class: 'GitSCM',
		branches: [[name: "*/${branch}"]],
		doGenerateSubmoduleConfigurations: false,
		extensions: [[$class: 'CleanCheckout']],
		submoduleCfg: [],
		userRemoteConfigs: [[credentialsId: 'bitbucket-svc1', url: "${url}"]]
	])

}
def executeMavenTests(threads,isRemote,browser,environment,retries,xmlFileName) {

    bat "mvn test -DthreadCount=${threads} -Dremote=${isRemote} -DBrowser=${browser} -Denv=${environment} -Dretry=${retries} -DsuiteFile=${xmlFileName}"
}
def archiveJavaArtifacts() {
	archiveArtifacts allowEmptyArchive: true, artifacts: '__test-results\\Report.html', followSymlinks: false
	testNG showFailedBuilds: true
	publishHTML([allowMissing: false,alwaysLinkToLastBuild: false,keepAll: false,reportDir: '__test-results',reportFiles: 'Report.html',reportName: 'Test Summary',reportTitles: ''])

}
def updateXRayWithTestNG(testPlan) {
	if ("${testPlan}" != 'NA' ){
			step(
			[$class: 'XrayImportBuilder', endpointName: '/testng/multipart', importFilePath: '**/testng-results.xml', importInParallel: 'false', importInfo: '''{
			"fields": {
				"project": {
					"key": "${testPlan.split('-')[0]}"
				},
				"summary": "Test Summary from Jenkins Build-${JOB_BASE_NAME}#${BUILD_NUMBER}", 
				"issuetype": {
				   "name": "Test Execution"
				}  
			},
			"xrayFields": {
					"testPlanKey": "${testPlan}"
				}
			}''', importToSameExecution: 'false', inputInfoSwitcher: 'fileContent', inputTestInfoSwitcher: 'filePath', serverInstance: 'CLOUD-4d5d4a26-3cb7-4838-a9ff-1b25e9f1cf55']
			)
	}
}
def sendEmail(emailRecipients) {
	 echo "${EMAIL_INFO}"
	String[] lines = EMAIL_INFO.split('\n')
	for (element in lines) {
		String[] data = element.split('=')
		switch(data[0].trim()) {
			case "tests_total":
				echo "total : ${data[1]}";
				break;
			case "tests_passed":
				echo "passed : ${data[1]}";
				break;
			case "tests_failed":
				echo "failed : ${data[1]}";
				break;
			case "tests_skipped":
				echo "skipped : ${data[1]}";
				break;
			default:
				echo "YAYYYYYYYY";
				break
		}
	}
  /* 	if ("${emailRecipients}" != 'NA' ){
		  str = info.split('\n')*/

	  /* echo "first element ${status[0]}"

	   /*echo "${lines}"
	  	for (element in lines) {
		    echo " boom : ${element}"
			temp = "${element.split('=')[0]};
			echo "${temp}"
		    try {
			     switch(temp) {
					case "tests_total":
					echo "total : ${element.split('=')[1]}";
					break;
					case "tests_passed":
					echo "passed : ${element.split('=')[1]}";
					break;
					case "tests_failed":
					echo "failed : ${element.split('=')[1]}";
					break;
					case "tests_skipped":
					echo "skipped: ${element.split('=')[1]}";
					break;
				    default:
					echo "YAYYYYYYYY";
					break
				}
			}catch(Exception err) {
				echo "${err}"
			}
		}*/

	//}


	  /*emailext body: '${FILE,path="__test-results/email-report.html"}', mimeType: 'text/html', subject: '$PROJECT_NAME - Build # $BUILD_NUMBER - $BUILD_STATUS', to: '${email_recipients}'*/


}

def getFileContent(template) {
	emailTemplate = readFile template;
	return emailTemplate;

}
return this