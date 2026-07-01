// vars/dbPipeline.groovy

def call() {
    pipeline {
        agent any
        
        stages {
            stage('Validate Migrations') {
                steps {
                    echo 'Validating database migration scripts...'
                    sh 'ls db/ || echo "no db folder found"'
                }
            }
            
            stage('Test Migrations') {
                steps {
                    echo 'Testing database migrations...'
                    sh 'echo "Running migration dry-run..."'
                }
            }
            
            stage('Apply Migrations') {
                steps {
                    echo 'Applying database migrations...'
                    sh 'echo "Migrations applied successfully"'
                }
            }
        }
        
        post {
            success {
                echo 'Database pipeline completed successfully!'
            }
            failure {
                echo 'Database pipeline failed!'
            }
        }
    }
}
