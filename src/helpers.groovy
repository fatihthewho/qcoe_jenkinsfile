import groovy.json.JsonOutput
import groovy.json.JsonSlurper

// not working with def
TEST_SUMMARY = [:]
THREAD_COUNT = 1
QCOE_MAIL = "venkata.kunta"
RETRY_COUNT = 0
// not working with out def
def PROJECT_LOCATION
def CSPROJ
def REPO_URL
def BRANCH
def EMAIL_RECIPIENTS
def CURRENT_DIR_PATH
def HUB_URL
def TEST_EXECUTION_VM
def TEST_ENVIRONMENT
def PARALLEL_EXECUTION
def XRAY_TEST_PLAN
def TEST_SUITES_FOLDER
def BROWSER


def setupGrid() {
    if (TEST_EXECUTION_VM.equalsIgnoreCase('NA')) {
        HUB_URL = "http://10.45.139.112:4444/"
        if (PARALLEL_EXECUTION) {
            THREAD_COUNT = 5

        }
    } else {
        def instanceId = autils.getInstanceID(TEST_EXECUTION_VM)
        autils.startAndWaitInstance(instanceId)
        HUB_URL = "http://${TEST_EXECUTION_VM}:4444/"
        if (PARALLEL_EXECUTION) {
            THREAD_COUNT = 2
        }
    }
    echo "THREAD_COUNT : ${THREAD_COUNT}"
}

def shutdown() {
    if (!TEST_EXECUTION_VM.equalsIgnoreCase('NA')) {
        def instanceId = autils.getInstanceID(TEST_EXECUTION_VM)
        autils.stopAndWaitInstance(instanceId)
    }
}

def checkoutRepo() {
    echo "checking out ${REPO_URL} ${BRANCH} "
    checkout([$class                           : 'GitSCM',
              branches                         : [[name: "*/${BRANCH}"]],
              doGenerateSubmoduleConfigurations: false,
              extensions                       : [[$class: 'CleanCheckout']],
              submoduleCfg                     : [],
              userRemoteConfigs                : [[credentialsId: 'bitbucket-svc1', url: "${REPO_URL}"]]
    ])

}

def compileCSharp(folder) {
    setProjectLocation(folder, CSPROJ)
    bat "dotnet build ${PROJECT_LOCATION}${CSPROJ}"
}

def compileMavenProject(folder) {
    setProjectLocation(folder, "pom.xml")
    bat 'mvn clean compile'
}

def setProjectLocation(folder, project) {
    std = powershell returnStdout: true, script: """(Get-ChildItem -Path ${folder} -Recurse -Filter ${project}).FullName"""
    PROJECT_LOCATION = std.trim().replace("\n", "").replace("${folder}\\", "").replace(project, "")
    echo "PL :${PROJECT_LOCATION}"
}

def executeNUnitTests(testSelection) {
    if(testSelection!=null){
        testSelection =testSelection.trim();
    }
    bat "nunit3-console ${PROJECT_LOCATION}${CSPROJ} --tp:env=${TEST_ENVIRONMENT} --tp:browser=${BROWSER} --tp:gridUrl=${HUB_URL} --workers=${THREAD_COUNT}  ${testSelection}"

}

def executeMavenTests(xmlFileName) {
    xmlFileName = xmlFileName.trim()
    bat "mvn test -Denv=${TEST_ENVIRONMENT} -DBrowser=${BROWSER} -DgridUrl=${HUB_URL} -DthreadCount=${THREAD_COUNT} -Dretry=${RETRY_COUNT} -DsuiteFile=${TEST_SUITES_FOLDER}/${xmlFileName}"
}

def archiveCSharpArtifacts() {
    echo "index.html archive"
    archiveArtifacts allowEmptyArchive: false, artifacts: "${PROJECT_LOCATION}__test-results/index.html", followSymlinks: false
    echo "Publish HTML"
    publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: false, reportDir: "${PROJECT_LOCATION}__test-results", reportFiles: 'index.html', reportName: 'Test Summary', reportTitles: ''])
    echo "Publish HTML COMPLETED"
}

def archiveJavaArtifacts() {
    archiveArtifacts allowEmptyArchive: false, artifacts: '__test-results\\Report.html', followSymlinks: false
    publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: false, reportDir: '__test-results', reportFiles: 'Report.html', reportName: 'Test Summary', reportTitles: ''])

}

def updateXRayWithNUnit() {
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
    if ("${XRAY_TEST_PLAN}" != 'NA') {
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

def extractFromLog() {
   def logContent = currentBuild.logFile.text
    env.testExecs = (logContent =~ /XRAY_TEST_EXECS:.*/).findAll().first()
    echo testExecs
}


def sendEmail(infraError) {
    if ("${EMAIL_RECIPIENTS}" != 'NA') {
        echo "${EMAIL_RECIPIENTS}"
        String mail = readFile "${CURRENT_DIR_PATH}/Templates/email-report.html"
        for (element in TEST_SUMMARY) {
            mail = mail.replace(element.key, element.value)
        }
        def html = 'email-report_temp.html'
        writeFile(file: "${html}", text: mail)
        emailext body: readFile("${html}"), mimeType: 'text/html', subject: '$PROJECT_NAME - Build # $BUILD_NUMBER - $BUILD_STATUS', to: """${EMAIL_RECIPIENTS}"""
        if (infraError) {
            emailext body: readFile("${html}"), mimeType: 'text/html', subject: 'Setup Failures in $PROJECT_NAME # $BUILD_NUMBER ', to: """${QCOE_MAIL}"""
        }
    }
}

def parseNUnitTestResults(filepath) {
    if (fileExists(filepath)) {
        int total
        int pass
        String partOfFile = readPartOfFile(filepath, 10)
        echo "${partOfFile}"
        def lines = partOfFile.split('\n')
        for (element in lines) {
            if (element.startsWith("<test-run")) {
                element = element.replace('\"', "")
                String[] temp = element.split(" ")
                for (item in temp) {
                    String[] data = item.split("=")
                    switch (data[0]) {
                        case "total":
                            total = data[1].replace('\"', "").toInteger()
                            TEST_SUMMARY["TOTAL_TESTS"] = total.toString()
                            break
                        case "passed":
                            pass = data[1].replace('\"', "").toInteger()
                            TEST_SUMMARY["TESTS_PASSED"] = pass.toString()
                            break
                        case "failed":
                            TEST_SUMMARY["TESTS_FAILED"] = data[1].replace('\"', "")
                            break
                        case "skipped":
                            TEST_SUMMARY["TESTS_SKIPPED"] = data[1].replace('\"', "")
                            break
                        default:
                            break
                    }
                }
                def percentage = (pass / total) * 100
                def formattedPercentage = String.format("%.1f%%", percentage).replace(".0%", "%")
                TEST_SUMMARY["PASS_PERCENTAGE"] = formattedPercentage
                println(TEST_SUMMARY)
                break
            }
        }
    } else {
        throw new Exception("NUnit Results file not found.")
    }

}

def parseTestNGTestResults(filepath) {
    int fail
    int pass
    int skip
    String partOfFile = readPartOfFile(filepath, 10)
    echo "${partOfFile}"
    def lines = partOfFile.split('\n')
    for (element in lines) {
        if (element.startsWith("<testng-results")) {
            element = element.replace('\"', "")
            String[] temp = element.split(" ")
            for (item in temp) {
                String[] data = item.split("=")
                switch (data[0]) {
                    case "passed":
                        pass = data[1].toInteger()
                        TEST_SUMMARY["TESTS_PASSED"] = pass.toString()
                        break
                    case "failed":
                        fail = data[1].toInteger()
                        TEST_SUMMARY["TESTS_FAILED"] = fail.toString()
                        break
                    case "skipped":
                        skip = data[1].replace('>', "").toInteger()
                        TEST_SUMMARY["TESTS_SKIPPED"] = skip.toString()
                        break
                    default:
                        break
                }
            }
            total = pass + fail + skip
            TEST_SUMMARY["TOTAL_TESTS"] = total.toString()
            def percentage = (pass / total) * 100
            def formattedPercentage = String.format("%.1f%%", percentage).replace(".0%", "%")
            TEST_SUMMARY["PASS_PERCENTAGE"] = formattedPercentage
            println(TEST_SUMMARY)
            break
        }

    }

}

def readPartOfFile(filePath, lines) {
    def cont = powershell returnStdout: true, script: """Get-Content ${filePath} | Select -First ${lines}"""
    return cont
}

def initialize(fileId) {
    echo "Triggered By: ${currentBuild.getBuildCauses().get(0)}"
    configFileProvider(
            [configFile(fileId: "${fileId}", variable: 'BUILD_CONFIG')]) {
        CONFIG = readJSON(file: BUILD_CONFIG)
        REPO_URL = CONFIG['REPO_URL'].trim()
        BRANCH = params.BRANCH.trim()
        if (BRANCH.equals('')) {
            BRANCH = CONFIG['BRANCH'].trim()
        }
        echo "BRANCH : ${BRANCH}"
        EMAIL_RECIPIENTS = params.EMAIL_RECIPIENTS.trim()
        if (EMAIL_RECIPIENTS.equals('')) {
            EMAIL_RECIPIENTS = CONFIG['EMAIL_RECIPIENTS'].trim()
        }
        echo "EMAIL_RECIPIENTS : ${EMAIL_RECIPIENTS}"
        TEST_ENVIRONMENT = params.TEST_ENVIRONMENT.trim()
        if (TEST_ENVIRONMENT.equals('')) {
            TEST_ENVIRONMENT = CONFIG['TEST_ENVIRONMENT'].trim()
        }
        echo "TEST_ENVIRONMENT : ${TEST_ENVIRONMENT}"
        TEST_EXECUTION_VM = params.TEST_EXECUTION_VM.trim()
        if (TEST_EXECUTION_VM.equals('')) {
            TEST_EXECUTION_VM = CONFIG['TEST_EXECUTION_VM'].trim()
        }
        echo "TEST_EXECUTION_VM : ${TEST_EXECUTION_VM}"
        XRAY_TEST_PLAN = params.XRAY_TEST_PLAN.trim()
        if (XRAY_TEST_PLAN.equals('')) {
            XRAY_TEST_PLAN = CONFIG['XRAY_TEST_PLAN'].trim()
        }
        echo "XRAY_TEST_PLAN : ${XRAY_TEST_PLAN}"
        PARALLEL_EXECUTION = params.PARALLEL_EXECUTION
        echo "PARALLEL_EXECUTION :${PARALLEL_EXECUTION}"
        BROWSER = params.BROWSER
        CSPROJ = CONFIG['CSPROJ']
        if (CSPROJ != null) {
            CSPROJ = CSPROJ.trim()
        } else {
            TEST_SUITES_FOLDER = CONFIG['TEST_SUITES_FOLDER']
            if (TEST_SUITES_FOLDER.equals('')) {
                TEST_SUITES_FOLDER = CONFIG['TEST_SUITES_FOLDER'].trim()
            }
            echo "TEST_SUITES_FOLDER :${TEST_SUITES_FOLDER}"

            RETRY_FAILED_TESTS = params.RETRY_FAILED_TESTS
            if (RETRY_FAILED_TESTS) {
                RETRY_COUNT = 1
            }
            echo "RETRY_COUNT :${RETRY_COUNT}"
        }
    }
    autils = load "${CURRENT_DIR_PATH}/src/aws.groovy"
}

return this

