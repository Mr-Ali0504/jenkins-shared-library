// vars/frontendPipeline.groovy

def call() {
    pipeline {
        agent any

        stages {
            stage('Checkout') {
                steps {
                    checkout scm
                }
            }

            stage('Gitleaks - Secret Scan') {
                steps {
                    sh '''
                        # Install gitleaks if not present
                        if ! command -v gitleaks &> /dev/null; then
                            curl -sSfL https://github.com/gitleaks/gitleaks/releases/download/v8.24.3/gitleaks_8.24.3_linux_x64.tar.gz | sudo tar -xz -C /usr/local/bin gitleaks
                        fi
                        
                        # Scan the repo for leaked secrets
                        gitleaks detect --source . --verbose --report-format json --report-path gitleaks-report.json || true
                    '''
                }
            }

            stage('Install Dependencies') {
                steps {
                    dir('frontend') {
                        sh 'npm install'
                    }
                }
            }

            stage('Build Frontend') {
                steps {
                    dir('frontend') {
                        sh 'npm run build'
                    }
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
                    dir('frontend') {
                        sh '''
                            # Install sonar-scanner if not present
                            if ! command -v sonar-scanner &> /dev/null; then
                                curl -sSLo /tmp/sonar-scanner.zip https://binaries.sonarsource.com/Distribution/sonar-scanner-cli/sonar-scanner-cli-6.2.1.4610-linux-x64.zip
                                sudo unzip -qo /tmp/sonar-scanner.zip -d /opt/
                                sudo mv /opt/sonar-scanner-*-linux-x64 /opt/sonar-scanner
                                sudo ln -sf /opt/sonar-scanner/bin/sonar-scanner /usr/local/bin/sonar-scanner
                                rm -f /tmp/sonar-scanner.zip
                            fi

                            sonar-scanner \
                                -Dsonar.host.url=http://localhost:9000 \
                                -Dsonar.token=${SONAR_TOKEN} || true
                        '''
                    }
                }
            }

            stage('Docker Build - Frontend') {
                steps {
                    sh 'docker build -t 1week-frontend:${BUILD_NUMBER} ./frontend'
                    echo "Frontend image built: 1week-frontend:${BUILD_NUMBER}"
                }
            }

            stage('Trivy - Image Scan') {
                steps {
                    sh '''
                        # Install Trivy if not present
                        if ! command -v trivy &> /dev/null; then
                            curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh | sudo sh -s -- -b /usr/local/bin
                        fi

                        # Scan the Docker image for vulnerabilities
                        echo "=== Scanning 1week-frontend:${BUILD_NUMBER} ==="
                        trivy image --severity HIGH,CRITICAL --format table 1week-frontend:${BUILD_NUMBER} || true

                        # Generate JSON report for archiving
                        trivy image --severity HIGH,CRITICAL --format json --output trivy-frontend-report.json 1week-frontend:${BUILD_NUMBER} || true
                    '''
                }
            }

            stage('Deploy Frontend to Nginx') {
                steps {
                    // Copy built static files to Nginx web root
                    sh 'sudo rm -rf /var/www/frontend/*'
                    sh 'sudo mkdir -p /var/www/frontend'
                    sh 'sudo cp -r frontend/dist/* /var/www/frontend/'

                    // Copy Nginx config and reload
                    sh 'sudo cp nginx.conf /etc/nginx/sites-available/1week-app'
                    sh 'sudo ln -sf /etc/nginx/sites-available/1week-app /etc/nginx/sites-enabled/1week-app'
                    sh 'sudo rm -f /etc/nginx/sites-enabled/default'
                    sh 'sudo nginx -t'
                    sh 'sudo systemctl reload nginx'
                }
            }
        }

        post {
            success {
                echo 'Frontend deployed successfully! Access at http://<SERVER_IP>/'
            }
            failure {
                echo 'Frontend pipeline failed!'
            }
        }
    }
}
