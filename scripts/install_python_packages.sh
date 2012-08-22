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

dir=$(cd $(dirname $0)/../; pwd)
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

#easy_install -i http://localhost:8302/simple/ gevent==1.0b3
pip install -i http://localhost:8302/simple/ -r $dir/requirements.txt

# pip install -i http://localhost:8302/simple/ -r $dir/requirements.txt
#pip install -r $dir/requirements.txt

if [[ "$context" == testing ]]; then
    pip install -r $dir/tests/requirements.txt
fi

pip install $dir

if [[ "$context" != testing ]]; then
    /usr/local/src/easystreet/scripts/pypi-server stop
fi

# NB: gevent_zeromq==0.2.3 breaks the bridge integration tests.
# Not a good sign.
# Do not upgrade until further investigation.
# # easy_install -i http://localhost:8302/simple/ gevent_zeromq==0.2.3


# ############## INSTALL DEPENDENCIES FROM OUR .externals ############

# if [[ "$context" == testing ]]; then
#     PACKAGES=$VIRTUAL_ENV/packages
#     mkdir -p $PACKAGES
#     # ginkgo, from twilio's master branch
#     (cd $PACKAGES &&
#         git clone git://code.corp.twilio.com/twilio/ginkgo &&
#         easy_install --allow-hosts=None -Z ./ginkgo
#         )

#     # pytwilio.shared
#     # This is already installed on unit by default, but we will install
#     # it here so that we can use eventually use --no-site-packages
#     # virtualenv. (Depends on using the bdist_egg of gevent & greenlet
#     # as provided by easystreet.)
#     pip install --no-deps -I -e git+git://code.corp.twilio.com/twilio/pytwilio.shared.git#egg=pytwilio.shared

#     # pytwilio.boxconfig for the agent's collector-discovery service.
#     # overwrite any previous installs / pytwilio.egg stuff confusing the virtualenv
#     pip install --no-deps -I -e git+git://code.corp.twilio.com/twilio/pytwilio.boxconfig.git#egg=pytwilio.boxconfig
# else
#     for package in ginkgo pytwilio/shared pytwilio/boxconfig; do
#         pushd /usr/local/src/$package
#         python setup.py clean
#         easy_install --allow-hosts=None -Z ./
#         popd
#     done
# fi

# ############################ INSTALL METRICS ###########################

# # easy_install --allow-hosts=None -Z $dir
