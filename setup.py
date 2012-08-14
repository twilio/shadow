#!/usr/bin/env python
#
# $Id: $

from setuptools import setup, Command, find_packages
# import os, shutil, stat, subprocess

# def get_base_path():
#     """
#     Use this if you need to install any non-python files found inside the
#     project tree
#     """
#     return os.path.split(os.path.abspath(__file__))[0]

# def get_python_bin_dir():
#     """
#     Use this if you need to install any files into the python bin dir
#     """
#     return os.path.join(sys.prefix, 'bin')

# def shell(cmdline):
#     return subprocess.Popen(cmdline, shell=True).wait()

# class TestCommand(Command):
#     """
#     Run tests with nose
#     """
#     description = "run tests with nose"
#     def initialize_options(self): pass
#     def finalize_options(self): pass
#     user_options = []

#     def run(self):
#         shell("nosetests tests --with-coverage --cover-html")

# class ServerSetup(Command):
#     """
#     Install system level files (things that require escalated privs)
#     """
#     description = "install system level files (init.d)"
#     def initialize_options(self): pass
#     def finalize_options(self): pass
#     user_options = []

#     def run(self):
#         if os.getuid() != 0:
#             print "You must be root to install server files."
#             raise SystemExit(1)
#         if not os.path.exists('/usr/local/sbin/pytwilio-runner'):
#             print ("/usr/local/sbin/pytwilio-runner not installed, "
#                    "it is required for this server.  Install it by doing"
#                    "sudo python setup.py server for pietwilio")
#             raise SystemExit(1)
#         print "installing /etc/init.d/shadow"
#         shutil.copyfile(os.path.join(get_base_path(), "init.d", "shadow"),
#             "/etc/init.d/shadow")
#         os.chmod("/etc/init.d/shadow", 0755)

#         #copy the conf file to /etc/gservice, making gservice dir if it doesn't
#         # exist
#         print "installing /etc/gservice/shadow.conf.py"
#         if not os.path.exists('/etc/gservice'):
#             os.mkdir('/etc/gservice')
#         shutil.copyfile(os.path.join(get_base_path(), "shadow.conf.py"),
#             "/etc/gservice/shadow.conf.py")

setup(name='shadow',
      version='0.1',
      author='Kelvin Law',
      author_email='kelvin@twilio.com',
      description='Shadow proxy',
      packages = find_packages('src'),
      package_dir = {'': 'src'},
      include_package_data = True,
      # cmdclass={'test': TestCommand,
                # 'server': ServerSetup}
      )
