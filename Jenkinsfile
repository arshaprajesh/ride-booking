
pipeline {
    agent any

    environment {
        RIDE_SERVICE_IMAGE = "ride-service:${BUILD_NUMBER}"
    }

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
                echo "Building Ride Service image: ${RIDE_SERVICE_IMAGE}"

                sh '''
                    docker build \
                        -f ride-service/Dockerfile \
                        -t "$RIDE_SERVICE_IMAGE" \
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

                        echo "Checking currently deployed image..."

                        PREVIOUS_IMAGE=$(docker inspect ride-booking-service \
                            --format '{{.Config.Image}}')

                        if [ -z "$PREVIOUS_IMAGE" ]; then
                            echo "Cannot determine previous image. Aborting deployment."
                            exit 1
                        fi

                        echo "Previous image: $PREVIOUS_IMAGE"

                        docker image inspect "$PREVIOUS_IMAGE" >/dev/null

                        echo "Deploying new image: $RIDE_SERVICE_IMAGE"

                        DEPLOY_FAILED=0

                        if ! RIDE_SERVICE_IMAGE="$RIDE_SERVICE_IMAGE" docker compose \
                            --env-file "$ENV_FILE" \
                            -p ride-booking \
                            -f docker-compose.yml \
                            up -d --no-deps --no-build ride-service
                        then
                            DEPLOY_FAILED=1
                            echo "Deployment command failed!"
                        fi

                        HEALTHY=0

                        if [ "$DEPLOY_FAILED" -eq 0 ]; then
                            echo "Waiting for new version readiness..."

                            for i in $(seq 1 30); do
                                if docker exec ride-booking-service \
                                    curl -fsS \
                                    http://localhost:8081/actuator/health/readiness
                                then
                                    HEALTHY=1
                                    echo "New Ride Service version is healthy!"
                                    break
                                fi

                                sleep 5
                            done
                        fi

                        if [ "$HEALTHY" -eq 1 ]; then
                            echo "Deployment successful!"
                            exit 0
                        fi

                        echo "New deployment failed. Starting rollback..."
                        docker logs --tail 100 ride-booking-service || true

                        echo "Restoring previous image: $PREVIOUS_IMAGE"

                        if ! RIDE_SERVICE_IMAGE="$PREVIOUS_IMAGE" docker compose \
                            --env-file "$ENV_FILE" \
                            -p ride-booking \
                            -f docker-compose.yml \
                            up -d --no-deps --no-build ride-service
                        then
                            echo "CRITICAL: Rollback deployment command failed!"
                            docker logs --tail 100 ride-booking-service || true
                            exit 1
                        fi

                        echo "Checking previous version health..."

                        for i in $(seq 1 30); do
                            if docker exec ride-booking-service \
                                curl -fsS \
                                http://localhost:8081/actuator/health/readiness
                            then
                                echo "Rollback successful! Previous version is healthy."
                                exit 1
                            fi

                            sleep 5
                        done

                        echo "CRITICAL: Rollback failed. Previous version is not healthy."
                        docker logs --tail 100 ride-booking-service || true
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
