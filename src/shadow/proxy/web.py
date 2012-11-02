from flask import Flask, request
import biplist
import json
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
        old_servers, new_servers,
        old_servers_timeout=5.0, new_servers_timeout=5.0,

        old_servers_additional_get_params=[],
        old_servers_additional_post_params=[],
        old_servers_additional_headers=[],

        new_servers_additional_get_params=[],
        new_servers_additional_post_params=[],
        new_servers_additional_headers=[],
        result_loggers=[]
        ):

        super(ProxyFlask, self).__init__(__name__)

        if len(old_servers) == 0:
            raise ProxyConfigError("At least 1 old server must be specified, old servers: {old_servers!r}". format(old_servers=old_servers))

        if len(new_servers) == 0:
            raise ProxyConfigError("At least 1 new server mush be specified, new server: {new_servers!r}".format(new_servers=new_servers))

        if not all([isinstance(x, AbstractResultsLogger) for x in result_loggers]):
            raise ProxyConfigError("result_loggers must all be sub-classes of AbstractResultsLogger: {result_loggers!r}".format(result_loggers=result_loggers))

        self.old_servers = old_servers
        self.new_servers = new_servers
        self.old_servers_timeout = old_servers_timeout
        self.new_servers_timeout = new_servers_timeout

        self.old_servers_additional_headers = old_servers_additional_headers
        self.old_servers_additional_post_params = old_servers_additional_post_params
        self.old_servers_additional_get_params = old_servers_additional_get_params

        self.new_servers_additional_headers = new_servers_additional_headers
        self.new_servers_additional_post_params = new_servers_additional_post_params
        self.new_servers_additional_get_params = new_servers_additional_get_params

        self.result_loggers = result_loggers
        self.route('/<path:path>', methods=['GET', 'POST', 'PUT', 'DELETE'])(self.catch_all)
        self.route('/', defaults={'path': ''}, methods=['GET', 'POST', 'PUT', 'DELETE'])(self.catch_all)
        self.service = service

        self.session = requests.session(config={
                'pool_connections': 20,
                'pool_maxsize': 20,
                'keep_alive': True,
            })

    def log_result(self, result):
        logger.debug('Logging results: {result!r}'.format(result=result))
        for results_log in self.result_loggers:
            try:
                results_log.log_result(result)
            except Exception:
                logger.exception("Results Logger: {results_log!r} encountered exception logging result: {result!r}".format(results_log=results_log, result=result))

    def format_response(self, response, elapsed_time=None):
        resp = None
        if isinstance(response, requests.Response):
            resp = {
                'headers': response.headers,
                'status_code': response.status_code,
                'body': self.parse_response(response),
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

    def format_request(self, url, method, headers, params, data, request):
        req = {
            'original': {
                'url': request.path,
                'method': request.method,
                'headers': dict([(unicode(k), unicode(v)) for k, v in request.headers.items()]),
                'get': dict([(unicode(k), unicode(v)) for k, v in request.args.items()]),
                'post': dict([(unicode(k), unicode(v)) for k, v in request.form.items()])
            },
            'modified': {
                'url': url,
                'method': method,
                'headers': headers,
                'get': params,
                'post': data
            }
        }
        return req

    def parse_response(self, response):
        headers = response.headers
        content_type = headers.get('content-type')
        if content_type == 'application/x-plist':
            try:
                result = json.dumps(biplist.readPlistFromString(response.content), indent=4, default=str)
            except Exception, e:
                result = response.content
                logger.warning("Response parser: {err}".format(err=e))
        else:
            result = response.text

        return result

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
        logger.info("Timed request [{elapsed!r}] with args {args!r}, {kwargs!r}".format(elapsed=elapsed, args=args, kwargs=kwargs))
        return (result, elapsed)

    def catch_all(self, path):
        method = request.method
        headers = dict([(k, v) for k, v in request.headers.items() if k not in ('Content-Length')])
        params = dict(request.args)
        data = dict(request.form)
        url = request.path

        true_greenlets = [self.service.spawn(self.timer, timed_func=self.session.request,
                method=method,
                url='{server}{path}'.format(server=service, path=path),
                headers=dict(headers.items() + self.old_servers_additional_headers),
                params=dict(params.items() + self.old_servers_additional_get_params),
                data=dict(data.items() + self.old_servers_additional_post_params),
                config=requests_config,
                timeout=self.old_servers_timeout
            ) for service in self.old_servers]

        shadow_greenlets = [self.service.spawn(self.timer, timed_func=self.session.request,
                method=method,
                url='{server}{path}'.format(server=service, path=path),
                headers=dict(headers.items() + self.new_servers_additional_headers),
                params=dict(params.items() + self.new_servers_additional_get_params),
                data=dict(data.items() + self.new_servers_additional_post_params),
                config=requests_config,
                timeout=self.new_servers_timeout
            ) for service in self.new_servers]

        greenlets = true_greenlets + shadow_greenlets

        self.service.spawn(self.process_greenlets, self.format_request(url, method, headers, params, data, request), greenlets)

        try:
            first_response = true_greenlets[0].get(block=True)[0]
            return (first_response.content, first_response.status_code, first_response.headers)
        except:
            return "Upstream old server error", 502
