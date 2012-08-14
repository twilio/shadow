# Application parameters
ui = {
    'port': 9000,
    'address': '0.0.0.0',
}

proxy = {
    'address': '0.0.0.0',
    'port': 8081,
    'true_servers': ['http://10.4.115.162:8081/'],
    'true_servers_timeout': 5.0,
    'shadow_servers': ['http://10.4.67.69:8081/'],
    'shadow_servers_timeout': 5.0,

    'shadow_servers_additional_get_params': [],
    'shadow_servers_additional_post_params': [],
    'shadow_servers_additional_headers': [],
}

# Daemon params
#chroot = "/var/shadow"
#user = "nobody"
#logfile = "/var/log/twilio/shadow-gservice.log"
#pidfile = "/var/run/shadow.pid"

service = 'shadow.service.ShadowService'

program_name = "shadow"

from pytwilio.log import make_log_config
import os

debug = False

file_prefix = '/var/log/twilio/'
logconfig = make_log_config('shadow', debug, {
        'handlers': {
            'ui': {
                'class': 'pytwilio.log.MultiFileHandler',
                'formatter': 'twilio',
                "filename": os.path.join(file_prefix, "{}-ui.log".format(program_name))
            },
            'proxy': {
                'class': 'pytwilio.log.MultiFileHandler',
                'formatter': 'twilio',
                "filename": os.path.join(file_prefix, "{}-proxy.log".format(program_name))
            },
            'results': {
                'class': 'pytwilio.log.MultiFileHandler',
                'formatter': 'twilio_pywsgi_access_fix',
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
    })
