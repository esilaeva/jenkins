pipeline {
    agent {
        label 'maven-cloud'
    }
    environment {
        TOKEN = credentials('token')
        CHAT_ID = credentials('chatID')
    }
    stages {
        stage('Prepare Environment') {
            steps {
                script {                    
                    echo "Preparing environment..."
                    env.TESTS_TYPE = params.TESTS_TYPE ?: null
                    echo "Configuration: TESTS_TYPE=${env.TESTS_TYPE}"

                    if (!env.TESTS_TYPE) {
                        error("TESTS_TYPE is not defined")
                    }
                }
            }
        }
        stage('Checkout') {
            steps {
                script {
                    checkout([$class: 'GitSCM', branches: [[name: "*/${BRANCH}"]], userRemoteConfigs: [[url: 'git@github.com:esilaeva/otus_homework.git', credentialsId: 'jenkins']]])
                }
            }
        }
        prepareConfig()
        stage('Build and Test') {
            steps {
                script {
                    if (env.TESTS_TYPE) {
                        def jobs = [:]
                        env.TESTS_TYPE.split(',').each { v ->
                            jobs["$v"] = {
                                node("maven-cloud") {
                                    stage("Running test $v") {
                                        triggerJob("$v")
                                    }
                                }
                            }
                        }
                        parallel jobs
                    } else {
                        error("TESTS_TYPE is not defined")
                    }
                }
            }
        }
    }
    post {
        always {
            script {
                if (env.TESTS_TYPE) {
                    sh "mkdir -p ./allure-results"
                    dir("allure-results") {
                        def jobs = [:]
                        env.TESTS_TYPE.split(',').each { v ->
                            jobs["$v"] = {
                                node("maven-cloud") {
                                    copyArtifacts projectName: "$v", selector: specific(v.getBuildNumber())
                                }
                            }
                        }
                        parallel jobs
                    }

                    allure includeProperties: false, jdk: '', reportBuildPolicy: 'ALWAYS', results: [[path: "${pwd()}/allure-results"]]

                    // Подготовка и отправка сообщения в Telegram
                    def buildStatus = currentBuild.currentResult
                    env.MESSAGE = "${env.JOB_NAME} ${buildStatus.toLowerCase()} for build #${env.BUILD_NUMBER}\n****************************************"
                    def allureReportUrl = "${env.BUILD_URL}allure"
                    try {
                        def summaryFile = "allure-report/widgets/summary.json"
                        if (fileExists(summaryFile)) {
                            def summary = sh(script: "cat ${summaryFile}", returnStdout: true).trim()
                            def jsonSlurper = new groovy.json.JsonSlurper()
                            def summaryJson = jsonSlurper.parseText(summary)
                            def passed = summaryJson.statistic.passed
                            def failed = summaryJson.statistic.failed
                            def skipped = summaryJson.statistic.skipped
                            def total = summaryJson.statistic.total
                            def error = total - passed - failed - skipped
                            env.REPORT_SUMMARY = "Passed: ${passed}, Failed: ${failed}, Error: ${error}, Skipped: ${skipped}\nTotal: ${total}"
                        } else {
                            env.REPORT_SUMMARY = "Summary report not found: ${summaryFile}"
                        }
                    } catch (Exception e) {
                        env.REPORT_SUMMARY = "Failed to read Allure report: ${e.message}"
                    }
                    sh """
                        curl -X POST -H 'Content-Type: application/json' -d '{ 
                            "chat_id": "${env.CHAT_ID}",
                            "text": "${env.MESSAGE}\\n${env.REPORT_SUMMARY}\\nAllure Report: ${allureReportUrl}",
                            "disable_notification": false
                        }' https://api.telegram.org/bot${env.TOKEN}/sendMessage
                    """
                } else {
                    error("TESTS_TYPE is not defined")
                }
            }
        }
    }
}

def triggerJob(def jobName) {
    build job: "$jobName"
}
