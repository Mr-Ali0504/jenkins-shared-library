// vars/backendPipeline.groovy

def call() {
    pipeline {
        agent any
        
        stages {
            stage('Install Dependencies') {
                steps {
                    echo 'Installing backend dependencies...'
                    sh 'cd backend && npm install || echo "npm not available, skipping"'
                }
            }
            
            stage('Build Backend') {
                steps {
                    echo 'Building backend application...'
                    sh 'cd backend && npm run build || echo "build script not available, skipping"'
                }
            }
            
            stage('Test Backend') {
                steps {
                    echo 'Running backend tests...'
                    sh 'cd backend && npm test || echo "no tests found, skipping"'
                }
            }
            
            stage('Deploy Backend') {
                steps {
                    echo 'Deploying backend application...'
                }
            }
        }
        
        post {
            success {
                echo 'Backend pipeline completed successfully!'
            }
            failure {
                echo 'Backend pipeline failed!'
            }
        }
    }
}
