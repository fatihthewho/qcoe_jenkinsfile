import groovy.json.JsonSlurperClassic
import groovy.json.JsonOutput
def EMAIL_INFO
def CURRENT_DIR_PATH

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
		echo "${testPlan}"
		step(
				[$class: 'XrayImportBuilder', endpointName: '/testng/multipart', importFilePath: '**/testng-results.xml', importInParallel: 'false', importInfo: """{
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
			}""", importToSameExecution: 'false', inputInfoSwitcher: 'fileContent', inputTestInfoSwitcher: 'filePath', serverInstance: 'CLOUD-4d5d4a26-3cb7-4838-a9ff-1b25e9f1cf55']
		)
	}
}

def updateXRayWithNUnit(testPlan){
	if ("${testPlan}" != 'NA') {
		echo "${testPlan}"
	def temp = """{
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
                }"""
	   echo "${temp}"

		step([$class: 'XrayImportBuilder', endpointName: '/nunit/multipart', importFilePath: 'TestResult.xml', importInParallel: 'false', importInfo: "${temp}", importToSameExecution: 'false', inputInfoSwitcher: 'fileContent', inputTestInfoSwitcher: 'fileContent', serverInstance: 'CLOUD-4d5d4a26-3cb7-4838-a9ff-1b25e9f1cf55', testImportInfo: '''{
                    "fields": {
                        "labels" : ["QCOE_Jenkins"]
                    }
                }'''])
	}
}
def sendEmail(emailRecipients) {
	echo "${EMAIL_INFO}"
	String mail = readFile "${CURRENT_DIR_PATH}/Templates/email-report.html"
	def lines = EMAIL_INFO.split('\n')
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
	def html = 'email-report_temp.html'
	writeFile(file: "${html}", text: mail)
	if ("${emailRecipients}" != 'NA' ){
		emailext body:  readFile("${html}"), mimeType: 'text/html', subject: '$PROJECT_NAME - Build # $BUILD_NUMBER - $BUILD_STATUS', to: '$email_recipients'
	}
}

def getFileContent(file) {
	temp = readFile file;
	return temp;

}
def executeMavenTests(threads, isRemote, browser, environment, retries, xmlFileName) {

	bat "mvn test -DthreadCount=${threads} -Dremote=${isRemote} -DBrowser=${browser} -Denv=${environment} -Dretry=${retries} -DsuiteFile=${xmlFileName}"
}

def archiveJavaArtifacts() {
	archiveArtifacts allowEmptyArchive: true, artifacts: '__test-results\\Report.html', followSymlinks: false
	testNG showFailedBuilds: true
	publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: false, reportDir: '__test-results', reportFiles: 'Report.html', reportName: 'Test Summary', reportTitles: ''])

}

return this