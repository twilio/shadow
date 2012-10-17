
#Shadow Proxy

##Introduction

Shadow is a HTTP debugging proxy that sits in front of an existing service and a service with new code changes. It copies and directs incoming requests to both downstream services and compares the responses from those services.

Shadowing deployments allows users to assert expected behaviors on the new codebase and detect unexpected behavioral changes before pushing new code into production.

Shadow comes with a UI that allows users to monitor the stream of requests live.

All request and responses are also logged in as a JSON (Default: log/shadow-results.log) for easy parsing and analysis.

For more information, check out our blog post on Shadow. 

## Installing

No escalated privledges needed unless you are using privileged ports.  This will install the core libraries into your site-packages directory.

	python setup.py install

## Configuring

There are 2 bundled configuration files:

debug_shadow.conf.py - for local development and debugging

shadow.conf.py - for running a shadow service

```python 

# Shadow specific configuration

ui = {
	# port and address for the UI
    'port': 9000, 
    'address': '0.0.0.0',
}

proxy = {

    # address and port shadow runs on
    'address': '0.0.0.0',
    'port': 8081,

	# server running the old versions of the code
    'old_servers': ['http://localhost:8081/'],
    
    # timeout values to use when making requests to old server
    'old_servers_timeout': 15.0,

	# Additional parameters that we want to add 
	# when making requests to the old server.
	# These will overwrite the existing params in the request
	
    'old_servers_additional_get_params': [],
    'old_servers_additional_post_params': [],
    'old_servers_additional_headers': [],

	# same parameters can be used for the new server
    'new_servers': ['http://localhost:8081/'],
    'new_servers_timeout': 15.0,

    'new_servers_additional_get_params': [],
    'new_servers_additional_post_params': [],
    'new_servers_additional_headers': [],
}

```
	
There are also Gingko specific configuration parameters for managing service and daemonization. 

Check out Ginkgo's [docs](http://ginkgo.readthedocs.org/en/latest/index.html) for more information on running Ginkgo services

## Running

Local development execution can be done with:

    ginkgo debug_shadow.conf.py

Server execution should be done with:

    ginkgoctl shadow.conf.py start

Be default, the UI can be accessed at [http://localhost:9000](http://localhost:9000)

##Testing

Tests are in the `tests` directory.  Run them using nose

    nosetests tests/


## Based upon

* [Ginkgo](https://github.com/progrium/ginkgo)
* [Gevent](http://www.gevent.org/)
* [Flask](http://flask.pocoo.org/)
* [Gevent-socketio](https://github.com/abourget/gevent-socketio)
* [Socketio](http://socket.io/)
* [AngularJS](http://angularjs.org/)
* [jsdiff](https://github.com/kpdecker/jsdiff)

