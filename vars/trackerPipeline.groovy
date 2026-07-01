// vars/trackerPipeline.groovy

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
                        if ! command -v gitleaks &> /dev/null; then
                            curl -sSfL https://github.com/gitleaks/gitleaks/releases/download/v8.24.3/gitleaks_8.24.3_linux_x64.tar.gz | sudo tar -xz -C /usr/local/bin gitleaks
                        fi
                        gitleaks detect --source . --verbose --report-format json --report-path gitleaks-report.json || true
                    '''
                }
            }

            stage('Deploy Tracker App') {
                steps {
                    // Create directory for the tracker app
                    sh 'sudo mkdir -p /var/www/tracker'
                    sh 'sudo cp devops-roadmap.html /var/www/tracker/index.html'

                    // Add Nginx config for tracker app on port 9090
                    sh '''
                        sudo tee /etc/nginx/sites-available/tracker-app > /dev/null <<EOF
server {
    listen 9090;
    server_name localhost;

    location / {
        root /var/www/tracker;
        index index.html;
        try_files \\$uri \\$uri/ /index.html;
    }
}
EOF
                    '''
                    sh 'sudo ln -sf /etc/nginx/sites-available/tracker-app /etc/nginx/sites-enabled/tracker-app'
                    sh 'sudo nginx -t'
                    sh 'sudo systemctl reload nginx'
                }
            }
        }

        post {
            success {
                echo 'Tracker App deployed successfully! Access at http://<SERVER_IP>:9090/'
            }
            failure {
                echo 'Tracker pipeline failed!'
            }
        }
    }
}
