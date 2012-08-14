"""
Fix pywsgi to use our python logging
"""


class LogFile(object):
    """A file-like wrapper around a logger object"""

    def __init__(self, logger):
        self.logger = logger

    def write(self, message):
        self.logger.info(message.strip())


def pywsgi_access_logger(logger):
    """
    This function will return a file-like object that will integrate with python
    logging and cause pywsgi to write access logs to our log files.  To use it
    pass the return value into the log= parameter for WSGIServer like this

        from pytwilio.log import pwsgi_access_logger
        gevent.wsgi.WSGIServer((config.HTTP_ADDRESS,
            config.HTTP_PORT), app, log=pywsgi_access_logger())

    """

    return LogFile(logger)
