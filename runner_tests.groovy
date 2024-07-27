timeout(60) {
    node("maven-cloud") {
        prepareConfig()
        wrap([$class: 'BuildUser']) {
            currentBuild.description = "User: $BUILD_USER"
        }
        stage("Checkout"){
            checkout git-api-tests
            git branch: $BRANCH, url: git@github.com:esilaeva/otus_homework.git
        }

        def jobs = [:]
        env.TESTS_TYPE.each(v-> {
            jobs["$v"] = node("maven-cloud")
                stage("Running test $v") {
                    triggerJob($v)
                }
        })

        parallel jobs

        stage("Publish allure report") {
            sh "mkdir -p ./allure-results"
            dir("allure-results"){
                jobs.each(k, v -> {
                    copyArtifacts projectName: $v, selector: specific(v.getBuildNumber())
                })
            }

            allure includeProperties: false, jdk: '', reportBuildPolicy: 'ALWAYS', results: [[path: "$pwd/allure-results"]]
        }
    }
}