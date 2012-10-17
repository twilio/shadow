import mock
from nose.tools import *


def test_proxy_service_init():

    import ginkgo
    import ginkgo.config

    ginkgo.settings = ginkgo.config.Config()

    address = 'testhost'
    port = 8888
    old_servers = ['true_server1', 'true_server2']
    new_servers = ['shadow_server1', 'shadow_server2']
    old_servers_timeout = 1337.0
    new_servers_timeout = 1338.0

    old_servers_additional_headers = [('test_true_header', 'test_true_value')]
    old_servers_additional_post_params = [('test_true_post', 'test_true_value')]
    old_servers_additional_get_params = [('test_true_get', 'test_true_value')]

    new_servers_additional_headers = [('testheader', 'testvalue')]
    new_servers_additional_post_params = [('testpost', 'testvalue')]
    new_servers_additional_get_params = [('testget', 'testvalue')]

    ginkgo.settings.load({
        'proxy': {
            'address': address,
            'port': port,
            'old_servers': old_servers,
            'new_servers': new_servers,
            'old_servers_timeout': old_servers_timeout,
            'new_servers_timeout': new_servers_timeout,

            'old_servers_additional_headers': old_servers_additional_headers,
            'old_servers_additional_post_params': old_servers_additional_post_params,
            'old_servers_additional_get_params': old_servers_additional_get_params,

            'new_servers_additional_headers': new_servers_additional_headers,
            'new_servers_additional_post_params': new_servers_additional_post_params,
            'new_servers_additional_get_params': new_servers_additional_get_params,
        }
    })

    import shadow.services.proxy
    app_flask = shadow.services.proxy.web.ProxyFlask = mock.Mock()
    app_flask.return_value = object()

    wsgi_server = shadow.services.proxy.WSGIServer = mock.Mock()

    result_logger = ['resultLoggers']

    proxy_service = shadow.services.proxy.ProxyService(result_logger)

    assert app_flask.called

    eq_(app_flask.call_count, 1)

    app_flask.assert_called_with(
        proxy_service,
        old_servers,
        new_servers,
        old_servers_timeout,
        new_servers_timeout,
        old_servers_additional_get_params,
        old_servers_additional_post_params,
        old_servers_additional_headers,
        new_servers_additional_get_params,
        new_servers_additional_post_params,
        new_servers_additional_headers,
        result_logger
    )

    assert wsgi_server.called

    eq_(wsgi_server.call_count, 1)

    wsgi_call_args = wsgi_server.call_args[0]

    eq_(wsgi_call_args[0], (address, port))
    eq_(id(wsgi_call_args[1]), id(app_flask.return_value))
