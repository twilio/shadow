#!/bin/bash
# Installs all the python packages needed for the metrics system.
#
# Usage: install_python_packages.sh [CONTEXT]
#
# If context == 'testing', then this script will *NOT* start
# easystreet but rather rely on it to already be running.
#
# TODO: remove "testing" context and simply detect whether or not
# easystreet is running.
#
# TODO: add "client" context which does not install non-client
# dependencies, namely pytwilio.boxconfig and requests 0.8.9.

set -e

dir=$(cd $(dirname $0)/../../; pwd)
context=$1

################ INSTALL DEPENDENCIES VIA EASYSTREET ###############

# These shenanigans are necessary because the metrics api depends
# on pytwilio/boxconfig, which in turn depends on requests==0.8.9,
# which differs from what's on the AMI (0.8.7) and which cannot
# be upgraded without substantial code review.
#
# We must also pull in greenlet & gevent ourselves here, since
# our virtualenv will only work if we install both into it
# (i.e. the system site packages flag does no good for gevent and
# greenlet).
#
# NB: we use easy_install for greenlet & gevent because these are
# actually bdist_egg's, since they contain code compiled against
# C extensions and libevent. (pip can only install source eggs)

if [[ "$context" != testing ]]; then
    /usr/local/src/easystreet/scripts/pypi-server start
    sleep 2 # hackery to wait until the pypi-server's actually started
fi

pip install -i http://localhost:8302/simple/ $dir

if [[ "$context" == testing ]]; then
    pip install -r $dir/tests/requirements.txt
fi

if [[ "$context" != testing ]]; then
    /usr/local/src/easystreet/scripts/pypi-server stop
fi


