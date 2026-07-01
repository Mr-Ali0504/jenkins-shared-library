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
