
Name: shadow
Author: Kelvin Law - kelvin@twilio.com
Created: Mon Aug  6 20:42:47 2012

Shadow proxy

======================================================================

How to use this service:






Local Installation:

    No escalated privledges needed.  This will install the core
    libraries into your site-packages directory.
    
        python setup.py install
    
Server Installation

    Root privledges needed.  This will install the init.d script and
    configuration files required to start and stop this service.  You
    should run this after running `python setup.py install`.

        sudo python setup.py server

Running:

    Local development execution can be done with

         python debug_shadow.conf.py

    Server execution should be done with 

         sudo /etc/init.d/shadow {start|stop|restart}

Testing:

    Tests are in the `tests` directory.  Run them using nose

        nosetests tests/

