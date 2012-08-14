#!/bin/sh
# Helper script for creating the common metrics virtualenv.
# Usage: make_virtualenv.sh /path/to/virtualenv [CONTEXT] [USE_EXISTING_ENV]
#
# Arguments:
#
#       1. path to virtual environment,
#       2. CONTEXT (if "testing", then packages are installed as they
#          would be on one of the Jenkins servers, and

set -e

dir=$(cd $(dirname $0)/../; pwd)
target=$1
context=$2

######################### CREATE VIRTUALENV ########################

mkdir -p $(dirname $target)
test ! -e $target || rm -rf $target
/usr/local/python/bin/virtualenv $target
. $target/bin/activate

######################## INSTALL PYTHON PACKAGES #######################

$dir/scripts/install_python_packages.sh $context
