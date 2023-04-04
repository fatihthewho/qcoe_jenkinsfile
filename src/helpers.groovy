
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

return this