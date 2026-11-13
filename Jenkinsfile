
pipeline {
    agent any

    options {
        timestamps()
    }

    stages {

        stage('Checkout') {
            steps {
                echo 'Checking out Ride Booking source code'
                checkout scm
            }
        }

        stage('Environment') {
            steps {
                echo 'Checking Java version'
                sh 'java -version'

                echo 'Checking Maven version'
                sh 'mvn -version'
            }
        }

        stage('Build and Test') {
            steps {
                echo 'Building Ride Booking and running tests'
                sh 'mvn -B clean verify'
            }
        }
    }

    post {
        always {
            junit(
                testResults: '**/target/surefire-reports/TEST-*.xml, **/target/failsafe-reports/TEST-*.xml',
                allowEmptyResults: true
            )
        }

        success {
            echo 'Ride Booking CI pipeline completed successfully!'
        }

        failure {
            echo 'Ride Booking CI pipeline failed. Check the console output.'
        }
    }
}
