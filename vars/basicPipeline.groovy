// vars/basicPipeline.groovy

def call() {
    pipeline {
        agent any
        
        stages {
            stage('Build') {
                steps {
                    echo 'Building from Shared Library Template...'
                    sayHello('Asgar')
                    
                    // Simulate creating an artifact
                    sh 'echo "This is the compiled app" > my-app-build.txt'
                }
            }
            
            stage('Test') {
                steps {
                    echo 'Testing from Shared Library Template...'
                }
            }
            
            stage('Deploy') {
                steps {
                    echo 'Deploying from Shared Library Template...'
                }
            }
        }
        
        post {
            always {
                echo 'Archiving artifacts from Shared Library Template...'
                archiveArtifacts artifacts: 'my-app-build.txt', fingerprint: true
            }
        }
    }
}
