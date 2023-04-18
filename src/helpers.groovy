// not working with def
TEST_SUMMARY = [:]
QCoE_Mail="venkata.kunta"
// not working with out def
def PROJECT_LOCATION
def CSPROJ
def REPO
def BRANCH
def EMAIL_IDS
def CURRENT_DIR_PATH
def HUB_URL


def setupGrid(ip) {
	if(ip.equals('-select-')){
		throw new Exception("select Execution VM")
	}
	if(ip.equals('qcoe_grid')){
		HUB_URL="http://10.45.139.112:4444/"
	}
	else{
		def instanceId= autils.getInstanceID(ip)
		autils.startAndWaitInstance(instanceId)
		HUB_URL="http://${ip}:4444/"
	}
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

def compileCSharp(folder,project){
	setProjectLocation(folder,project)
	bat "dotnet build ${PROJECT_LOCATION}${project}"
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
def executeNUnitTests(environment,browser,threads,testSelection) {

	bat "nunit3-console ${PROJECT_LOCATION}${CSPROJ} --tp:env=${environment} --tp:browser=${browser} --tp:gridUrl=${HUB_URL} --workers=${threads}  ${testSelection}"

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
def updateXRayWithNUnit(testPlan){
	echo "NUnit Test Results"
	nunit testResultsPattern: "${PROJECT_LOCATION}TestResult.xml"
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
		def results = "${PROJECT_LOCATION}TestResult.xml"

		step([$class: 'XrayImportBuilder', endpointName: '/nunit/multipart', importFilePath: """${results}""", importInParallel: 'false', importInfo: "${temp}", importToSameExecution: 'false', inputInfoSwitcher: 'fileContent', inputTestInfoSwitcher: 'fileContent', serverInstance: 'CLOUD-4d5d4a26-3cb7-4838-a9ff-1b25e9f1cf55', testImportInfo: '''{
                    "fields": {
                        "labels" : ["QCOE_Jenkins"]
                    }
                }'''])
	}
}
def updateXRayWithTestNG(testPlan) {
	echo "TestNG Test Results"
	testNG showFailedBuilds: true
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
		emailext body: readFile("${html}"), mimeType: 'text/html', subject: 'Setup Failures in $PROJECT_NAME # $BUILD_NUMBER ', to: """${QCoE_Mail}"""
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
	}
	autils = load "${CURRENT_DIR_PATH}/src/aws.groovy"

}
def shutdown(ip) {
	if(!ip.equals('qcoe_grid')){
		def instanceId= autils.getInstanceID(ip)
		autils.stopAndWaitInstance(instanceId)
	}
}

return this

