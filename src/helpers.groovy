import groovy.json.JsonSlurperClassic
import groovy.json.JsonOutput

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
def executeNUnitTests(threads,isRemote,browser,environment,testSelection) {
	bat "nunit3-console TestAutomation.csproj --workers=${threads} --tp:remote=${isRemote} --tp:browser=${browser} --tp:env=${environment} ${testSelection}"
}

def archiveCSharpArtifacts(){
	archiveArtifacts allowEmptyArchive: true, artifacts: '__test-results\\index.html', followSymlinks: false
	nunit testResultsPattern: 'TestResult.xml'
	publishHTML([allowMissing: false,alwaysLinkToLastBuild: false,keepAll: false,reportDir: '__test-results',reportFiles: 'index.html',reportName: 'Test Summary',reportTitles: ''])

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
def prepareEmail() {
	 echo "${EMAIL_INFO}"
	String[] lines = EMAIL_INFO.split('\n')
/*	for (element in lines) {
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
	}*/

}

def getFileContent(template) {
	emailTemplate = readFile template;
	return emailTemplate;

}

return this