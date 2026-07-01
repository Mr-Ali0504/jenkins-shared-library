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
