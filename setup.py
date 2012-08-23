#!/usr/bin/env python
#
# $Id: $

from setuptools import setup, find_packages

setup(name='shadow',
      version='0.1',
      author='Kelvin Law',
      author_email='kelvin@twilio.com',
      description='Shadow proxy',
      packages = find_packages('src'),
      package_dir = {'': 'src'},
      include_package_data = True,
      install_requires = [
         # 3rd party
        'gevent==1.0b3',
        'ginkgo==twilio-rc2',
        'flask==0.9',
        'requests==0.13.6',
        'gevent-socketio==0.3.5-rc2',

        # internal dependencies
        'pytwilio.log==0.1',
      ]
      )
