// vars/sayHello.groovy

def call(String name = 'User') {
    echo "=========================================="
    echo "Hello, ${name}! This is coming from the Shared Library."
    echo "=========================================="
}
