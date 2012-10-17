# Application parameters
ui = {
    'port': 9000,
    'address': '0.0.0.0',
}

proxy = {

    # address and port shadow runs on
    'address': '0.0.0.0',
    'port': 8081,


    # old server or server containing existing code
    'old_servers': ['http://localhost:8081/'],
    'old_servers_timeout': 15.0,

    'old_servers_additional_get_params': [],
    'old_servers_additional_post_params': [],
    'old_servers_additional_headers': [],

    # new server or server contain new code
    'new_servers': ['http://localhost:8081/'],
    'new_servers_timeout': 15.0,

    'new_servers_additional_get_params': [],
    'new_servers_additional_post_params': [],
    'new_servers_additional_headers': [],
}

# Daemon params
# chroot = "/mnt/services/shadow/chroot"
user = "nobody"

service = 'shadow.service.ShadowService'

program_name = "shadow"

import os

debug = False

file_prefix = './logs/'

logconfig = {
        'version': 1,
        'formatters': {
            "default": {
                "format": "[%(asctime)s,%(msecs)03d] %(levelname) 7s - \"-\" [%(filename)s:%(lineno)d %(funcName)s] | %(message)s",
                "datefmt": "%d/%b/%Y %H:%M:%S"
            },
            "default_pywsgi_access_fix": {
                "format": "%(message)s"
            }
        },
        'handlers': {
            'ui': {
                'class': 'logging.FileHandler',
                'formatter': 'default',
                "filename": os.path.join(file_prefix, "{}-ui.log".format(program_name))
            },
            'proxy': {
                'class': 'logging.FileHandler',
                'formatter': 'default',
                "filename": os.path.join(file_prefix, "{}-proxy.log".format(program_name))
            },
            'results': {
                'class': 'logging.FileHandler',
                'formatter': 'default_pywsgi_access_fix',
                "filename": os.path.join(file_prefix, "{}-results.log".format(program_name))
            }
        },
        'loggers': {
            'shadow.proxy': {
                'level': 'INFO',
                'propogate': 0,
                'handlers': ['proxy']
            },
            'shadow.ui': {
                'level': 'INFO',
                'propogate': 0,
                'handlers': ['ui']
            },
            'shadow.results': {
                'level': 'INFO',
                'propogate': 0,
                'handlers': ['results']
            },
            'shadow': {
                'level': 'INFO',
                'propogate': 0,
                'handlers': ['main']
            }
        }
    }
