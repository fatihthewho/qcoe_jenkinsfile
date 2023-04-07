import groovy.json.JsonSlurperClassic
import groovy.json.JsonOutput
EMAIL_INFO
CURRENT_DIR_PATH

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
def prepareEmail(emailRecipients) {
	 echo "${EMAIL_INFO}"
	String mail = readFile "${CURRENT_DIR_PATH}/Templates/email-repot.html"
	def lines = EMAIL_INFO.split('\n')
	println(lines[0])
	for (element in lines){
		def data=element.split(',')
		switch (data[0]){
			case  "total_tests":
				mail= mail.replace("TOTAL_TESTS", data[1].trim())
				break
			case  "passed_tests":
				println(data[1].trim())
				mail =mail.replace("TESTS_PASSED", data[1].trim())
				break
			case  "failed_tests":
				println(data[1].trim())
				mail =mail.replace("TESTS_FAILED", data[1].trim())
				break
			case  "skipped_tests":
				println(data[1].trim())
				mail = mail.replace("TESTS_SKIPPED", data[1].trim())
				break
			default :
				break
		}
	}
	writeFile(file: 'email-report1.html', text: mail)

}

def getFileContent(file) {
	temp = readFile template;
	return emailTemplate;

}

return this