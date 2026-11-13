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

                echo 'Checking Docker version'
                sh 'docker version'
            }
        }

        stage('Build and Test') {
            steps {
                echo 'Building Ride Booking and running tests'
                sh 'mvn -B clean verify'
            }
        }

        stage('Docker Build') {
            steps {
                echo 'Building Ride Service Docker image'

                sh '''
                    docker build \
                        -f ride-service/Dockerfile \
                        -t ride-service:${BUILD_NUMBER} \
                        -t ride-service:latest \
                        .
                '''

                echo 'Docker image built successfully!'
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
