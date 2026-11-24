
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

                echo 'Checking Docker Compose version'
                sh 'docker compose version'
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

        stage('Deploy Ride Service') {
            steps {
                withCredentials([
                    file(
                        credentialsId: 'ride-booking-env',
                        variable: 'ENV_FILE'
                    )
                ]) {
                    sh '''
                        set -eu
                        set +x

                        echo "Deploying Ride Service..."

                        docker compose \
                            --env-file "$ENV_FILE" \
                            -p ride-booking \
                            -f docker-compose.yml \
                            up -d --no-deps --no-build ride-service

                        echo "Waiting for Ride Service readiness..."

                        for i in $(seq 1 30); do
                            if docker exec ride-booking-service \
                                curl -fsS \
                                http://localhost:8081/actuator/health/readiness
                            then
                                echo "Ride Service is healthy!"
                                exit 0
                            fi

                            sleep 5
                        done

                        echo "Ride Service readiness check failed"
                        docker logs --tail 100 ride-booking-service
                        exit 1
                    '''
                }
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
            echo 'Ride Booking CI/CD pipeline completed successfully!'
        }

        failure {
            echo 'Ride Booking CI/CD pipeline failed. Check the console output.'
        }
    }
}
