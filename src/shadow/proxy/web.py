from flask import Flask, request
import logging
import requests
import time

logger = logging.getLogger('shadow.proxy.web')

requests_config = {
    'safe_mode': True,
}

class ProxyConfigError(Exception): pass


class AbstractResultsLogger(object):
    def log_result(self, result):
        raise NotImplementedError()


class ProxyFlask(Flask):

    def __init__(self, service,
        true_servers, shadow_servers,
        true_servers_timeout=5.0, shadow_servers_timeout=5.0,

        true_servers_additional_get_params=[],
        true_servers_additional_post_params=[],
        true_servers_additional_headers=[],

        shadow_servers_additional_get_params=[],
        shadow_servers_additional_post_params=[],
        shadow_servers_additional_headers=[],
        result_loggers=[]
        ):

        super(ProxyFlask, self).__init__(__name__)

        if len(true_servers) == 0:
            raise ProxyConfigError("At least 1 true server must be specified, true servers: {}". format(true_servers))

        if len(shadow_servers) == 0:
            raise ProxyConfigError("At least 1 shadow servers mush be specified, shadow servers: {}".format(shadow_servers))

        if not all([isinstance(x, AbstractResultsLogger) for x in result_loggers]):
            raise ProxyConfigError("result_loggers must all be sub-classes of AbstractResultsLogger: {}".format(result_loggers))

        self.true_servers = true_servers
        self.shadow_servers = shadow_servers
        self.true_servers_timeout = true_servers_timeout
        self.shadow_servers_timeout = shadow_servers_timeout

        self.true_servers_additional_headers = true_servers_additional_headers
        self.true_servers_additional_post_params = true_servers_additional_post_params
        self.true_servers_additional_get_params = true_servers_additional_get_params

        self.shadow_servers_additional_headers = shadow_servers_additional_headers
        self.shadow_servers_additional_post_params = shadow_servers_additional_post_params
        self.shadow_servers_additional_get_params = shadow_servers_additional_get_params

        self.result_loggers = result_loggers
        self.route('/<path:path>', methods=['GET', 'POST', 'PUT', 'DELETE'])(self.catch_all)
        self.route('/', defaults={'path': ''}, methods=['GET', 'POST', 'PUT', 'DELETE'])(self.catch_all)
        self.service = service

    def log_result(self, result):
        logger.debug('Logging results: {}'.format(result))
        for results_log in self.result_loggers:
            try:
                results_log.log_result(result)
            except Exception:
                logger.exception("Results Logger: {} encountered exception logging result: {}".format(results_log, result))

    def format_response(self, response, elapsed_time=None):
        resp = None
        if isinstance(response, requests.Response):
            resp = {
                'headers': response.headers,
                'status_code': response.status_code,
                'body': response.text,
                'elapsed_time': elapsed_time,
                'type': 'http_response'
            }
        elif isinstance(response, Exception):
            resp = {
                'type': 'exception',
                'message': response.message,
                'repr': repr(response),
                'elapsed_time': elapsed_time
            }
        else:
            resp = {
                'type': 'unknown',
                'repr': repr(response),
                'elapsed_time': elapsed_time
            }
        return resp

    def format_request(self, request):
        req = {
            'url': request.path,
            'method': request.method,
            'headers': dict([(unicode(k), unicode(v)) for k, v in request.headers.items()]),
            'get': dict([(unicode(k), unicode(v)) for k, v in request.args.items()]),
            'post': dict([(unicode(k), unicode(v)) for k, v in request.form.items()])
        }
        return req

    def process_greenlets(self, request, greenlets):
        results = []
        for greenlet in greenlets:
            response, elapsed_time = greenlet.get(block=True)
            results.append((response, elapsed_time))
        formatted_results = [self.format_response(response, elapsed_time) for response, elapsed_time in results]
        formatted_request = request
        msg = {'request': formatted_request, 'results': formatted_results}
        self.log_result(msg)

    def timer(self, timed_func, *args, **kwargs):
        start = time.time()
        try:
            result = timed_func(*args, **kwargs)
        except Exception, e:
            logger.exception("Exception encountered in time step")
            result = e
        elapsed = time.time() - start
        logger.info("Timed request [{}] with args {}, {}".format(elapsed, args, kwargs))
        return (result, elapsed)

    def catch_all(self, path):
        method = request.method
        headers = dict([(k, v) for k, v in request.headers.items() if v != ''])
        params = dict(request.args)
        data = dict(request.form)
        true_greenlets = [self.service.spawn(self.timer, timed_func=requests.request,
                method=method,
                url='{server}{path}'.format(server=service, path=path),
                headers=dict(headers.items() + self.true_servers_additional_headers),
                params=dict(params.items() + self.true_servers_additional_get_params),
                data=dict(data.items() + self.true_servers_additional_post_params),
                config=requests_config,
                timeout=self.true_servers_timeout
            ) for service in self.true_servers]

        shadow_greenlets = [self.service.spawn(self.timer, timed_func=requests.request,
                method=method,
                url='{server}{path}'.format(server=service, path=path),
                headers=dict(headers.items() + self.shadow_servers_additional_headers),
                params=dict(params.items() + self.shadow_servers_additional_get_params),
                data=dict(data.items() + self.shadow_servers_additional_post_params),
                config=requests_config,
                timeout=self.shadow_servers_timeout
            ) for service in self.shadow_servers]

        greenlets = true_greenlets + shadow_greenlets

        self.service.spawn(self.process_greenlets, self.format_request(request), greenlets)

        try:
            first_response = true_greenlets[0].get(block=True)[0]
            return (first_response.content, first_response.status_code, first_response.headers)
        except:
            return "Upstream true server error", 502
