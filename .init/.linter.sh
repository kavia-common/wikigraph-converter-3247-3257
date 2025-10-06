#!/bin/bash
cd /home/kavia/workspace/code-generation/wikigraph-converter-3247-3257/backend
./gradlew checkstyleMain
LINT_EXIT_CODE=$?
if [ $LINT_EXIT_CODE -ne 0 ]; then
   exit 1
fi

