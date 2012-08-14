import logging

from ginkgo import Service, settings
from ginkgo.async.gevent import WSGIServer

from ..common import logfile
from ..proxy import web

logger = logging.getLogger('shadow.proxy')


class ProxyService(Service):
    address = settings.get('proxy', {}).get('address', 'localhost')
    port = settings.get('proxy', {}).get('port', 8081)

    true_servers = settings.get('proxy', {}).get('true_servers', ['http://localhost:8000/'])
    shadow_servers = settings.get('proxy', {}).get('shadow_servers', ['http://localhost:8000/'])
    true_servers_timeout = settings.get('proxy', {}).get('true_servers_timeout', 5.0)
    shadow_servers_timeout = settings.get('proxy', {}).get('shadow_servers_timeout', 5.0)

    true_servers_additional_headers = settings.get('proxy', {}).get('true_servers_additional_headers', [])
    true_servers_additional_post_params = settings.get('proxy', {}).get('true_servers_additional_post_params', [])
    true_servers_additional_get_params = settings.get('proxy', {}).get('shadow_servers_additional_get_params', [])

    shadow_servers_additional_headers = settings.get('proxy', {}).get('shadow_servers_additional_headers', [])
    shadow_servers_additional_post_params = settings.get('proxy', {}).get('shadow_servers_additional_post_params', [])
    shadow_servers_additional_get_params = settings.get('proxy', {}).get('shadow_servers_additional_get_params', [])

    def do_start(self):
        logger.info("Starting ProxyService on {}:{}".format(self.address, self.port))

    def do_stop(self):
        logger.info("Stopping ProxyService")

    def __init__(self, result_loggers=[]):
            self.app = web.ProxyFlask(self,
                self.true_servers, self.shadow_servers,
                self.true_servers_timeout, self.shadow_servers_timeout,
                self.shadow_servers_additional_headers,
                self.shadow_servers_additional_post_params,
                self.shadow_servers_additional_get_params,
                result_loggers)

            self.server = WSGIServer(
                (self.address, self.port), self.app,
                log=logfile.pywsgi_access_logger(logger))

            self.add_service(self.server)
