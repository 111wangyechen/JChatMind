#!/bin/sh
# ============================================
# JChatMind Test Runner - Unix/Mac
# ============================================
echo "Running JChatMind unit tests..."
echo ""

cd "$(dirname "$0")/.."

if [ -f "./mvnw" ]; then
    ./mvnw test
else
    mvn test
fi

RESULT=$?

if [ $RESULT -ne 0 ]; then
    echo ""
    echo "============================================"
    echo " TESTS FAILED!"
    echo "============================================"
    exit 1
fi

echo ""
echo "============================================"
echo " ALL TESTS PASSED!"
echo "============================================"
exit 0
