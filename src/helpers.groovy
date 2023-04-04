
def info(msg) {
	echo "checking out ${msg}"
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

def archiveArtifacts() {
    archiveArtifacts "${PROJECT_LOCATION}/bin/${SLN_CONFIG}/logs/**/*.*"
    if(ARTIFACT_PATTERN.length()>0){
        for(artifactPath in ARTIFACT_PATTERN.split(';')){
            artifactFileName = artifactPath.tokenize('\\').last()
            projectDirNoSlash = "${PROJECT_LOCATION}".substring(1,"${PROJECT_LOCATION}".length())
            archiveArtifacts "${projectDirNoSlash}/bin/${SLN_CONFIG}/${artifactFileName}"
        }
    }
}
return this