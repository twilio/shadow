from flask import Flask
from flask import send_file

app = Flask(__name__)


@app.route('/')
def index():
    return send_file('static/index.html')
