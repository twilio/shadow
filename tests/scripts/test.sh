#!/bin/sh
# Test runner script for Jenkins. Depends on build.sh.

source _env/bin/activate
mkdir -p tests-output/cobertura tests-output/xunit
nosetests tests/unit \
       --with-xcoverage \
    --cover-package shadow \
       --xcoverage-file tests-output/cobertura/coverage.xml \
       --with-xunit --xunit-file tests-output/xunit/output.xml
