// vars/aksPipeline.groovy

def call() {
    pipeline {
        agent any

        environment {
            ACR_NAME       = 'levelupacr'
            ACR_LOGIN_SVR  = 'levelupacr.azurecr.io'
            AKS_CLUSTER    = 'levelup'
            AKS_RG         = 'level-up'
            IMAGE_TAG      = "${env.BUILD_NUMBER}"
        }

        stages {
            stage('Checkout') {
                steps {
                    checkout scm
                }
            }

            stage('Gitleaks - Secret Scan') {
                steps {
                    sh '''
                        if ! command -v gitleaks &> /dev/null; then
                            curl -sSfL https://github.com/gitleaks/gitleaks/releases/download/v8.24.3/gitleaks_8.24.3_linux_x64.tar.gz | sudo tar -xz -C /usr/local/bin gitleaks
                        fi
                        gitleaks detect --source . --verbose --report-format json --report-path gitleaks-report.json || true
                    '''
                }
            }

            stage('SonarQube - Code Analysis') {
                steps {
                    script {
                        // Fetch SonarQube token from AWS Secrets Manager
                        env.SONAR_TOKEN = sh(
                            script: 'aws secretsmanager get-secret-value --secret-id sonarqube/token --query SecretString --output text --region us-east-1',
                            returnStdout: true
                        ).trim()
                    }
                    sh '''
                        # Install sonar-scanner if not present
                        if ! command -v sonar-scanner &> /dev/null; then
                            curl -sSLo /tmp/sonar-scanner.zip https://binaries.sonarsource.com/Distribution/sonar-scanner-cli/sonar-scanner-cli-6.2.1.4610-linux-x64.zip
                            sudo unzip -qo /tmp/sonar-scanner.zip -d /opt/
                            sudo mv /opt/sonar-scanner-*-linux-x64 /opt/sonar-scanner
                            sudo ln -sf /opt/sonar-scanner/bin/sonar-scanner /usr/local/bin/sonar-scanner
                            rm -f /tmp/sonar-scanner.zip
                        fi
                    '''

                    // Scan backend
                    dir('backend') {
                        sh '''
                            sonar-scanner \
                                -Dsonar.host.url=http://localhost:9000 \
                                -Dsonar.token=${SONAR_TOKEN} || true
                        '''
                    }

                    // Scan frontend
                    dir('frontend') {
                        sh '''
                            sonar-scanner \
                                -Dsonar.host.url=http://localhost:9000 \
                                -Dsonar.token=${SONAR_TOKEN} || true
                        '''
                    }
                }
            }

            stage('Docker Build') {
                steps {
                    sh "docker build -t ${ACR_LOGIN_SVR}/1week-frontend:${IMAGE_TAG} -t ${ACR_LOGIN_SVR}/1week-frontend:latest ./frontend"
                    sh "docker build -t ${ACR_LOGIN_SVR}/1week-backend:${IMAGE_TAG} -t ${ACR_LOGIN_SVR}/1week-backend:latest ./backend"
                }
            }

            stage('Push to ACR') {
                steps {
                    sh "az acr login --name ${ACR_NAME}"
                    sh "docker push ${ACR_LOGIN_SVR}/1week-frontend:${IMAGE_TAG}"
                    sh "docker push ${ACR_LOGIN_SVR}/1week-frontend:latest"
                    sh "docker push ${ACR_LOGIN_SVR}/1week-backend:${IMAGE_TAG}"
                    sh "docker push ${ACR_LOGIN_SVR}/1week-backend:latest"
                }
            }

            stage('Deploy to AKS') {
                steps {
                    // Get AKS credentials
                    sh "az aks get-credentials --resource-group ${AKS_RG} --name ${AKS_CLUSTER} --overwrite-existing"

                    // Apply all K8s manifests
                    sh 'kubectl apply -f k8s/namespace.yaml'
                    sh 'kubectl apply -f k8s/database-deployment.yaml'
                    sh 'kubectl apply -f k8s/backend-deployment.yaml'
                    sh 'kubectl apply -f k8s/frontend-deployment.yaml'

                    // Update the image tags to force a rolling update
                    sh "kubectl set image deployment/backend backend=${ACR_LOGIN_SVR}/1week-backend:${IMAGE_TAG} -n 1week-app"
                    sh "kubectl set image deployment/frontend frontend=${ACR_LOGIN_SVR}/1week-frontend:${IMAGE_TAG} -n 1week-app"

                    // Wait for rollout
                    sh 'kubectl rollout status deployment/database -n 1week-app --timeout=120s'
                    sh 'kubectl rollout status deployment/backend -n 1week-app --timeout=120s'
                    sh 'kubectl rollout status deployment/frontend -n 1week-app --timeout=120s'
                }
            }

            stage('Verify') {
                steps {
                    sh 'kubectl get pods -n 1week-app'
                    sh 'kubectl get svc -n 1week-app'
                }
            }
        }

        post {
            success {
                echo 'AKS deployment successful! Run: kubectl get svc -n 1week-app to get the external IP.'
            }
            failure {
                echo 'AKS deployment failed!'
            }
        }
    }
}
