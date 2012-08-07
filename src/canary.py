from flask import Flask
from flask import request
import gevent
import requests
import logging
import time
from gevent import monkey
monkey.patch_all()

app = Flask(__name__)

log = logging.getLogger('canary')
log.setLevel(logging.DEBUG)
stream_handler = logging.StreamHandler()
stream_handler.setLevel(logging.DEBUG)
formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
stream_handler.setFormatter(formatter)
log.addHandler(stream_handler)

services = ['http://10.210.154.239:8081/', 'http://10.4.67.69:8081/']

requests_config = {
    'safe_mode': True,
}


def timer(func, *args, **kwargs):
    start = time.time()
    result = func(*args, **kwargs)
    elapsed = time.time() - start
    return (result, elapsed)


def process_greenlets(request, greenlets):
    results = []
    for greenlet in greenlets:
        try:
            res = greenlet.get(block=True)
            resp = res[0]
            if resp.ok:
                result = resp.content
            else:
                result = resp.content
        except Exception, e:
            result = e
        results.append((result, resp.status_code, res[1]))
    log.info("Results for request: {}, responses received: {}".format(request, results))


@app.route('/', defaults={'path': ''}, methods=['GET', 'POST', 'PUT'])
@app.route('/<path:path>', methods=['GET', 'POST', 'PUT'])
def catch_all(path):
    method = request.method
    headers = dict([(k,v) for k,v in request.headers if v != ''])
    params = dict(request.args)
    data = dict(request.form)
    greenlets = [gevent.spawn(timer, func=requests.request,
            method=method,
            url='{server}{path}'.format(server=service, path=path),
            headers=headers,
            params=params,
            data=data,
            config=requests_config,
            timeout=5.0
        ) for service in services]
    gevent.spawn(process_greenlets, request.url, greenlets)

    first_response = greenlets[0].get(block=True)[0]
    return (first_response.content, first_response.status_code, first_response.headers)

if __name__ == "__main__":

    app.run(host="0.0.0.0", port=8081)
