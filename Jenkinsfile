pipeline {
  agent any
  stages {
    stage('Build') {
      steps {
        echo 'Build Demo Application'
      }
    }

    stage('Linux Tests') {
      parallel {
        stage('Linux Tests') {
          steps {
            echo 'Run Linux tests'
            sh 'sh run_linux_tests.sh'
          }
        }

        stage('Windows Tests') {
          steps {
            echo 'Run Windows tests'
          }
        }

      }
    }

    stage('Specify Staging') {
      steps {
        echo 'Deploy to staging environment'
        input 'Ok to deploy to production'
      }
    }

    stage('Deploy Production') {
      steps {
        echo 'Deploy to Prod'
      }
    }

  }
  post {
    always {
      archiveArtifacts(artifacts: 'target/demoapp.jar', fingerprint: true)
    }

    failure {
      mail(to: 'vitalij@pathomation.com', subject: "Failed Pipeline ${currentBuild.fullDisplayName}", body: " For details about the failure, see ${env.BUILD_URL}")
    }

  }
}