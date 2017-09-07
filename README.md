
# Shadow Proxy

## Introduction

Shadow is a HTTP debugging proxy that sits in front of an existing service and a service with new code changes. It copies and directs incoming requests to both downstream services and compares the responses from those services.

Shadowing deployments allows users to assert expected behaviors on the new codebase and detect unexpected behavioral changes before pushing new code into production.

Shadow comes with a UI that allows users to monitor the stream of requests live.

## Requirements

1. Java 1.6+
2. SBT 0.12.1+

## Building

1. Clone the repository: `git clone https://github.com/twilio/shadow.git`
2. Build the JAR: `sbt assembly`

This generates an assembled Jar with all dependencies in `/target/shadow-assembly-<VERSION>.jar`


## Installing

No escalated privledges needed unless you are using privileged ports. Just copy the Jar built in the previous step on to your existing server.

## Configuring

Make a copy of the example configuration `application.conf.example` and change the settings to fit your environment.


## Shadow specific configuration

	akka {
	  loglevel = INFO
	}
	
	shadow {
	    version = "0.1-SNAPSHOT"
	
	    proxy-host = localhost
	    proxy-port = 8000
	
	    ui-host = localhost
	    ui-port = 8081
	
	    results-log = "logs/results.log"
	
	    trueServer {
	
	        host = "httpbin.org"
	        port = 80
	
	        query-param-overrides {
	        }
	
	        form-param-overrides {
	        }
	
	    }
	
	    shadowServer {
	
	        host = "httpbin.org"
	        port = 80
	
	        query-param-overrides {
	            test = ["hello"]
	        }
	
	        form-param-overrides {
	            test2 = ["world"]
	        }
	
	    }
	}

	spray.can.server {
	  server-header = shadow-server/${shadow.version}
	  request-timeout = 15s
	  stats-support = true
	}
	
	spray.can.client {
	    user-agent-header = shadow/${shadow.version}
	}
	
## Running

Running `java -jar -Dconfig.file=application.conf shadow-assembly-<VERSION>.jar` will start the server in the foreground. 

We recommend using a supervisor such as `jsvc` or `runit` to manage running shadow

Be default, the UI can be accessed at [http://localhost:8081](http://localhost:8081)

## Testing

Tests are found under `src/main/test/scala/`

To run them, use: `sbt test`

## Build Status
[![Build Status](https://travis-ci.org/twilio/shadow.png?branch=scala)](http://travis-ci.org/twilio/shadow?branch=scala)

## Based upon

* [AngularJS](http://angularjs.org/)
* [jsdiff](https://github.com/kpdecker/jsdiff)
* [Scala](http://www.scala.org)
* [Spray](http://www.spray.io)
* [Akka](http://www.akka.io)

