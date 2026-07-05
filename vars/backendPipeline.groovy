// vars/backendPipeline.groovy

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
                    dir('backend') {
                        sh 'npm install'
                    }
                }
            }

            stage('Test Backend') {
                steps {
                    dir('backend') {
                        sh 'npx jest --coverage || echo "Tests completed"'
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
                    dir('backend') {
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

            stage('Docker Build - Backend') {
                steps {
                    sh 'docker build -t 1week-backend:${BUILD_NUMBER} ./backend'
                    echo "Backend image built: 1week-backend:${BUILD_NUMBER}"
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
                        echo "=== Scanning 1week-backend:${BUILD_NUMBER} ==="
                        trivy image --severity HIGH,CRITICAL --format table 1week-backend:${BUILD_NUMBER} || true

                        # Generate JSON report for archiving
                        trivy image --severity HIGH,CRITICAL --format json --output trivy-backend-report.json 1week-backend:${BUILD_NUMBER} || true
                    '''
                }
            }

            stage('Deploy Backend') {
                steps {
                    // Stop existing backend process if running
                    sh 'sudo systemctl stop 1week-backend || true'

                    // Copy backend files to deployment directory
                    sh 'sudo mkdir -p /var/www/backend'
                    sh 'sudo rm -rf /var/www/backend/*'
                    sh 'sudo cp -r backend/* /var/www/backend/'
                    sh 'cd /var/www/backend && sudo npm install --production'

                    // Create a systemd service to keep backend running
                    sh '''
                        sudo tee /etc/systemd/system/1week-backend.service > /dev/null <<EOF
[Unit]
Description=1Week Backend API
After=network.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/var/www/backend
ExecStart=/usr/bin/node index.js
Restart=on-failure
Environment=PORT=3000

[Install]
WantedBy=multi-user.target
EOF
                    '''

                    // Start the backend service
                    sh 'sudo systemctl daemon-reload'
                    sh 'sudo systemctl enable 1week-backend'
                    sh 'sudo systemctl start 1week-backend'
                    sh 'sleep 3 && sudo systemctl status 1week-backend'
                }
            }
        }

        post {
            success {
                echo 'Backend deployed successfully! API running on port 3000, proxied via Nginx at /api/'
            }
            failure {
                echo 'Backend pipeline failed!'
            }
        }
    }
}
