import logging

from ginkgo import Service, settings
from ginkgo.async.gevent import WSGIServer

from ..common import logfile
from ..proxy import web

logger = logging.getLogger('shadow.proxy')


class ProxyService(Service):
    address = settings.get('proxy', {}).get('address', 'localhost')
    port = settings.get('proxy', {}).get('port', 8081)

    old_servers = settings.get('proxy', {}).get('old_servers', ['http://localhost:8000/'])
    old_servers_timeout = settings.get('proxy', {}).get('old_servers_timeout', 5.0)

    old_servers_additional_headers = settings.get('proxy', {}).get('old_servers_additional_headers', [])
    old_servers_additional_post_params = settings.get('proxy', {}).get('old_servers_additional_post_params', [])
    old_servers_additional_get_params = settings.get('proxy', {}).get('old_servers_additional_get_params', [])

    new_servers = settings.get('proxy', {}).get('new_servers', ['http://localhost:8000/'])
    new_servers_timeout = settings.get('proxy', {}).get('new_servers_timeout', 5.0)

    new_servers_additional_headers = settings.get('proxy', {}).get('new_servers_additional_headers', [])
    new_servers_additional_post_params = settings.get('proxy', {}).get('new_servers_additional_post_params', [])
    new_servers_additional_get_params = settings.get('proxy', {}).get('new_servers_additional_get_params', [])

    def do_start(self):
        logger.info("Starting ProxyService on {}:{}".format(self.address, self.port))

    def do_stop(self):
        logger.info("Stopping ProxyService")

    def __init__(self, result_loggers=[]):
            self.app = web.ProxyFlask(self,
                self.old_servers, self.new_servers,
                self.old_servers_timeout, self.new_servers_timeout,

                self.old_servers_additional_get_params,
                self.old_servers_additional_post_params,
                self.old_servers_additional_headers,

                self.new_servers_additional_get_params,
                self.new_servers_additional_post_params,
                self.new_servers_additional_headers,
                result_loggers)

            self.server = WSGIServer(
                (self.address, self.port), self.app,
                log=logfile.pywsgi_access_logger(logger))

            self.add_service(self.server)
