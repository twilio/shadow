import logging

from ginkgo import Service, settings

from ginkgo.async.gevent import ServerWrapper

from ..common import logfile
from ..ui import web

from socketio.server import SocketIOServer

logger = logging.getLogger('shadow.ui')


class UIService(Service):
    address = settings.get('ui', {}).get('address', 'localhost')
    port = settings.get('ui', {}).get('port', 9000)

    def broadcast_message(self, event, args, ns_name=''):
        logger.debug("Broadcasting Event: {} Data: {}".format(event, args))
        try:
            socket_server = self.server
        except Exception:
            logger.exception("UIService socket server not ready")
        pkt = dict(type="event",
               name=event,
               args=args,
               endpoint=ns_name)
        for sessid, socket in socket_server.sockets.iteritems():
            socket.send_packet(pkt)

    def do_start(self):
        logger.info("Starting UIService on {}:{}".format(self.address, self.port))

    def do_stop(self):
        logger.info("Stopping UIService")

    def __init__(self):
        self.app = web.app
        self.server = SocketIOServer(
            (self.address, self.port), self.app,
            log=logfile.pywsgi_access_logger(logger),
            resource='socket.io',
            policy_server=False)

        self.add_service(ServerWrapper(self.server))
