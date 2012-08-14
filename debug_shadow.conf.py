# Application parameters
ui = {
    'port': 9000,
    'address': 'localhost',
}

proxy = {
    'address': 'localhost',
    'port': 8081,
    'true_servers': ['http://10.4.115.162:8081/'],
    'true_servers_timeout': 5.0,
    'shadow_servers': ['http://10.4.67.69:8081/'],
    'shadow_servers_timeout': 5.0,

    'shadow_servers_additional_get_params': [],
    'shadow_servers_additional_post_params': [],
    'shadow_servers_additional_headers': [],
}

service = 'shadow.service.ShadowService'

program_name = "shadow"

from pytwilio.log import make_log_config
import os

debug = True

file_prefix = './log/'
logconfig = make_log_config('shadow', True, {
        'handlers': {
            'console': {
                "class": "logging.StreamHandler",
                "formatter": "twilio",
                "stream": "ext://sys.stderr"
            },
            'results': {
                'class': 'pytwilio.log.MultiFileHandler',
                'formatter': 'twilio_pywsgi_access_fix',
                "filename": os.path.join(file_prefix, "{}-results.log".format(program_name))
            }
        },
        'loggers': {
            'shadow': {
                'level': 'DEBUG',
                'propogate': 0,
                'handlers': ['console']
            },
            'shadow.results': {
                'level': 'DEBUG',
                'propogate': 0,
                'handlers': ['results']
            }
        }
    })
