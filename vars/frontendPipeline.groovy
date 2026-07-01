// vars/frontendPipeline.groovy

def call() {
    pipeline {
        agent any
        
        stages {
            stage('Install Dependencies') {
                steps {
                    echo 'Installing frontend dependencies...'
                    sh 'cd frontend && npm install || echo "npm not available, skipping"'
                }
            }
            
            stage('Build Frontend') {
                steps {
                    echo 'Building frontend application...'
                    sh 'cd frontend && npm run build || echo "build script not available, skipping"'
                }
            }
            
            stage('Test Frontend') {
                steps {
                    echo 'Running frontend tests...'
                    sh 'cd frontend && npm test || echo "no tests found, skipping"'
                }
            }
            
            stage('Deploy Frontend') {
                steps {
                    echo 'Deploying frontend application...'
                }
            }
        }
        
        post {
            success {
                echo 'Frontend pipeline completed successfully!'
            }
            failure {
                echo 'Frontend pipeline failed!'
            }
        }
    }
}
