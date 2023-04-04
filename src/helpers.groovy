import groovy.json.JsonSlurperClassic
import groovy.json.JsonOutput
TOTAL_TIME = "temp"
def info(msg) {
	echo "MESSAGE : ${msg}"
}
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
def sendEmail(pass,fail,skipped,browser,environment,threads,timeTaken,emailRecipients) {
   if ("${emailRecipients}" != 'NA' ){
   
	  emailext body: '${FILE,path="__test-results/email-report.html"}', mimeType: 'text/html', subject: '$PROJECT_NAME - Build # $BUILD_NUMBER - $BUILD_STATUS', to: '${email_recipients}'
   }
             
}
def prepareEmailableReport(msg) {
    echo ${TOTAL_TIME};
	
}
def getFileContent(template) {
	emailTemplate = readFile template;
	return emailTemplate;

}
return this