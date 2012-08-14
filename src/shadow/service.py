from gevent import monkey
monkey.patch_socket()
monkey.patch_ssl()

from ginkgo import Service

import logging
import json

from .services import ui, proxy
from .proxy import web as proxy_web

logger = logging.getLogger('shadow.main')


class WSRequestLogger(proxy_web.AbstractResultsLogger):
    def __init__(self, ui_service):
        self.ui_service = ui_service

    def log_result(self, msg):
        self.ui_service.broadcast_message("results", msg)


class LogFileRequestLogger(proxy_web.AbstractResultsLogger):
    js_log = logging.getLogger('shadow.results')

    def log_result(self, msg):
        self.js_log.info(json.dumps(msg))


class ShadowService(Service):

    def __init__(self):
        self.ui_service = ui.UIService()
        self.ws_request_logger = WSRequestLogger(self.ui_service)
        self.log_request_logger = LogFileRequestLogger()
        self.proxy_service = proxy.ProxyService(result_loggers=[self.ws_request_logger, self.log_request_logger])
        self.add_service(self.ui_service)
        self.add_service(self.proxy_service)

    def do_start(self):
        logger.info("Starting ShadowService")

    def do_stop(self):
        logger.info("Stopping ShadowService")
