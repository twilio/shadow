#!/usr/bin/env python
#
# $Id: $

from setuptools import setup, find_packages

setup(name='shadow',
      version='0.1',
      author='Kelvin Law',
      author_email='kelvin@twilio.com',
      description='Shadow proxy',
      packages=find_packages('src'),
      package_dir={'': 'src'},
      include_package_data=True,
      dependency_links=[
        'https://github.com/SiteSupport/gevent/tarball/1.0b4#egg=gevent-1.0dev',
        'https://github.com/progrium/ginkgo/tarball/master#egg=ginkgo-0.5.0dev'
      ],
      install_requires=[
        'gevent==1.0dev',
        'ginkgo==0.5.0dev',
        'flask==0.9',
        'requests==0.13.6',
        'gevent-socketio==0.3.5-rc2',
        'Cython==0.17.1',
      ]
)
