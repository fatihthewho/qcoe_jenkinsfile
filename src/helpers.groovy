// not working with def
TEST_SUMMARY = [:]
THREAD_COUNT = 1
QCOE_MAIL="venkata.kunta"
// not working with out def
def PROJECT_LOCATION
def CSPROJ
def REPO
def BRANCH
def EMAIL_IDS
def CURRENT_DIR_PATH
def HUB_URL
def EXECUTION_VM
def TEST_ENVIRONMENT
def PARALLEL_EXECUTION
def RETRY_FAILED_TESTS
def XRAY_TEST_PLAN
def TEST_SUITES_FOLDER

def setupGrid() {
	if(EXECUTION_VM.equalsIgnoreCase('use-qcoe-grid')){
		HUB_URL="http://10.45.139.112:4444/"
		if(PARALLEL_EXECUTION){
			THREAD_COUNT=5
			echo "${THREAD_COUNT}"
		}
	}
	else{
		def instanceId= autils.getInstanceID(EXECUTION_VM)
		autils.startAndWaitInstance(instanceId)
		HUB_URL="http://${EXECUTION_VM}:4444/"
		if(PARALLEL_EXECUTION){
			THREAD_COUNT=2
		}
	}
}
def shutdown() {
	if(!EXECUTION_VM.equalsIgnoreCase('use-qcoe-grid')){
		def instanceId= autils.getInstanceID(EXECUTION_VM)
		autils.stopAndWaitInstance(instanceId)
	}
}

def checkoutRepo(){
	echo "checking out ${REPO} ${BRANCH} "
	checkout([$class: 'GitSCM',
			  branches: [[name: "*/${BRANCH}"]],
			  doGenerateSubmoduleConfigurations: false,
			  extensions: [[$class: 'CleanCheckout']],
			  submoduleCfg: [],
			  userRemoteConfigs: [[credentialsId: 'bitbucket-svc1', url: "${REPO}"]]
	])

}

def compileCSharp(folder){
	setProjectLocation(folder,CSPROJ)
	bat "dotnet build ${PROJECT_LOCATION}${CSPROJ}"
}
def compileMavenProject(folder,project){
	setProjectLocation(folder,project)
	bat 'mvn clean compile'
}
def setProjectLocation(folder,project){
	std = powershell returnStdout: true, script: """(Get-ChildItem -Path ${folder} -Recurse -Filter ${project}).FullName"""
	PROJECT_LOCATION = std.trim().replace("\n", "").replace("${folder}\\","").replace(project,"")
	echo "PL :${PROJECT_LOCATION}"
}
def executeNUnitTests(browser,testSelection) {

	bat "nunit3-console ${PROJECT_LOCATION}${CSPROJ} --tp:env=${TEST_ENVIRONMENT} --tp:browser=${browser} --tp:gridUrl=${HUB_URL} --workers=${THREAD_COUNT}  ${testSelection}"

}
def executeMavenTests(environment, browser, threads, retries, xmlFileName) {
	bat "mvn test -Denv=${environment} -DBrowser=${browser} -DgridUrl=${HUB_URL} -DthreadCount=${threads} -Dretry=${retries} -DsuiteFile=${xmlFileName}"
}

def archiveCSharpArtifacts(){
	echo "index.html archive"
	archiveArtifacts allowEmptyArchive: false, artifacts: "${PROJECT_LOCATION}__test-results/index.html", followSymlinks: false
	echo "Publish HTML"
	publishHTML([allowMissing: false,alwaysLinkToLastBuild: false,keepAll: false,reportDir: "${PROJECT_LOCATION}__test-results",reportFiles: 'index.html',reportName: 'Test Summary',reportTitles: ''])
	echo "Publish HTML COMPLETED"
}
def archiveJavaArtifacts() {
	archiveArtifacts allowEmptyArchive: false, artifacts: '__test-results\\Report.html', followSymlinks: false
	publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: false, reportDir: '__test-results', reportFiles: 'Report.html', reportName: 'Test Summary', reportTitles: ''])

}
def updateXRayWithNUnit(){
	echo "NUnit Test Results"
	nunit testResultsPattern: "${PROJECT_LOCATION}TestResult.xml"
	if ("${XRAY_TEST_PLAN}" != 'NA') {
		echo "${XRAY_TEST_PLAN}"
	def temp = """{
                    "fields": {
                        "project": {
                            "key": "${XRAY_TEST_PLAN.split('-')[0]}"
                        },
                        "summary": "Test Summary from Jenkins Build-${JOB_BASE_NAME}#${BUILD_NUMBER}", 
                        "issuetype": {
                           "name": "Test Execution"
                        }  
                },
                "xrayFields": {
					"testPlanKey": "${XRAY_TEST_PLAN}"
				}
                }"""
	   echo "${temp}"
		def results = "${PROJECT_LOCATION}TestResult.xml"

		step([$class: 'XrayImportBuilder', endpointName: '/nunit/multipart', importFilePath: """${results}""", importInParallel: 'false', importInfo: "${temp}", importToSameExecution: 'false', inputInfoSwitcher: 'fileContent', inputTestInfoSwitcher: 'fileContent', serverInstance: 'CLOUD-4d5d4a26-3cb7-4838-a9ff-1b25e9f1cf55', testImportInfo: '''{
                    "fields": {
                        "labels" : ["QCOE_Jenkins"]
                    }
                }'''])
	}
}
def updateXRayWithTestNG() {
	echo "TestNG Test Results"
	testNG showFailedBuilds: true
	if ("${XRAY_TEST_PLAN}" != 'NA' ){
		echo "${XRAY_TEST_PLAN}"
		step(
				[$class: 'XrayImportBuilder', endpointName: '/testng/multipart', importFilePath: '**/testng-results.xml', importInParallel: 'false', importInfo: """{
			"fields": {
				"project": {
					"key": "${XRAY_TEST_PLAN.split('-')[0]}"
				},
				"summary": "Test Summary from Jenkins Build-${JOB_BASE_NAME}#${BUILD_NUMBER}", 
				"issuetype": {
				   "name": "Test Execution"
				}  
			},
			"xrayFields": {
					"testPlanKey": "${XRAY_TEST_PLAN}"
				}
			}""", importToSameExecution: 'false', inputInfoSwitcher: 'fileContent', inputTestInfoSwitcher: 'filePath', serverInstance: 'CLOUD-4d5d4a26-3cb7-4838-a9ff-1b25e9f1cf55']
		)
	}
}
def sendEmail(infraError) {
	if ("${EMAIL_IDS}" != 'NA' ){
		echo "${EMAIL_IDS}"
		String mail = readFile "${CURRENT_DIR_PATH}/Templates/email-report.html"
		for (element in TEST_SUMMARY){
			mail = mail.replace(element.key, element.value)
		}
		def html = 'email-report_temp.html'
		writeFile(file: "${html}", text: mail)
		emailext body:  readFile("${html}"), mimeType: 'text/html', subject: '$PROJECT_NAME - Build # $BUILD_NUMBER - $BUILD_STATUS', to: """${EMAIL_IDS}"""
		if(infraError) {
		emailext body: readFile("${html}"), mimeType: 'text/html', subject: 'Setup Failures in $PROJECT_NAME # $BUILD_NUMBER ', to: """${QCOE_MAIL}"""
		}
	}
}






def parseNUnitTestResults(filepath) {
	if (fileExists(filepath)) {
		int total
		int pass
		String partOfFile = readPartOfFile(filepath,10)
		echo "${partOfFile}"
		def lines = partOfFile.split('\n')
		for (element in lines) {
			if (element.startsWith("<test-run")){
				element = element.replace('\"',"")
				String[] temp=element.split(" ")
				for(item in temp) {
					String[] data = item.split("=")
					switch (data[0]) {
						case "total":
							total = data[1].replace('\"',"").toInteger()
							TEST_SUMMARY["TOTAL_TESTS"] = total.toString()
							break
						case "passed":
							pass = data[1].replace('\"',"").toInteger()
							TEST_SUMMARY["TESTS_PASSED"] = pass.toString()
							break
						case "failed":
							TEST_SUMMARY["TESTS_FAILED"] = data[1].replace('\"',"")
							break
						case "skipped":
							TEST_SUMMARY["TESTS_SKIPPED"] = data[1].replace('\"',"")
							break
						default:
							break
					}
				}
				def percentage = (pass / total ) * 100
				def formattedPercentage = String.format("%.1f%%", percentage).replace(".0%","%")
				TEST_SUMMARY["PASS_PERCENTAGE"]= formattedPercentage
				println(TEST_SUMMARY)
				break
			}

		}
	}
	else {
		throw new Exception("NUnit Results file not found.")
	}

}
def parseTestNGTestResults(filepath) {
	int fail
	int pass
	int skip
	String partOfFile = readPartOfFile(filepath,10)
	echo "${partOfFile}"
	def lines = partOfFile.split('\n')
	for (element in lines) {
		if (element.startsWith("<testng-results")){
			element = element.replace('\"',"")
			String[] temp=element.split(" ")
			for(item in temp) {
				String[] data = item.split("=")
				switch (data[0]) {
					case "passed":
						pass = data[1].toInteger()
						TEST_SUMMARY["TESTS_PASSED"] = pass.toString()
						break
					case "failed":
						fail= data[1].toInteger()
						TEST_SUMMARY["TESTS_FAILED"] = fail.toString()
						break
					case "skipped":
						skip = data[1].replace('>',"").toInteger()
						TEST_SUMMARY["TESTS_SKIPPED"] = skip.toString()
						break
					default:
						break
				}
			}
			total= pass+fail+skip
			TEST_SUMMARY["TOTAL_TESTS"] = total.toString()
			def percentage = (pass / total ) * 100
			def formattedPercentage = String.format("%.1f%%", percentage).replace(".0%","%")
			TEST_SUMMARY["PASS_PERCENTAGE"]= formattedPercentage
			println(TEST_SUMMARY)
			break
		}

	}

}
def readPartOfFile(filePath,lines){

	def cont = powershell returnStdout: true, script: """Get-Content ${filePath} | Select -First 10"""
	return cont
}

def initialize(fileId){
	echo "Triggered By: ${currentBuild.getBuildCauses().get(0)}"
	configFileProvider(
			[configFile(fileId: "${fileId}", variable: 'BUILD_CONFIG')]) {
		CONFIG = readJSON(file: BUILD_CONFIG)
		CSPROJ = CONFIG['CSPROJ']
		REPO = CONFIG['REPO']
		BRANCH = CONFIG['BRANCH']
		EMAIL_IDS=CONFIG['EMAIL']
		TEST_ENVIRONMENT = params.TEST_ENVIRONMENT.trim()
		if(TEST_ENVIRONMENT.trim().equalsIgnoreCase('pre-defined')){
			TEST_ENVIRONMENT = CONFIG['TEST_ENVIRONMENT'].trim()
		}
		EXECUTION_VM = params.EXECUTION_VM.trim()
		if(EXECUTION_VM.trim().equalsIgnoreCase('pre-defined')){
			EXECUTION_VM = CONFIG['EXECUTION_VM'].trim()
		}
		XRAY_TEST_PLAN=params.XRAY_TEST_PLAN.trim()
		if(XRAY_TEST_PLAN.trim().equalsIgnoreCase('pre-defined')){
			XRAY_TEST_PLAN = CONFIG['XRAY_TEST_PLAN'].trim()
		}

		TEST_SUITES_FOLDER=CONFIG['TEST_SUITES_FOLDER']
		echo "Test Results :${TEST_SUITES_FOLDER}"
		PARALLEL_EXECUTION=params.PARALLEL_EXECUTION
		echo "Parallel :${PARALLEL_EXECUTION}"
	}
	autils = load "${CURRENT_DIR_PATH}/src/aws.groovy"

}


return this

