var mqtt    = require('mqtt');
var URL = require('url')


//var client  = mqtt.connect('mqtt://test.mosquitto.org');
var client  = mqtt.connect('mqtt://127.0.0.1');
var http = require('http');

function sendMessage(num) {
	client.publish('/server/broad', num);
}
client.on('message', function (topic, message) {
  // message is Buffer
    console.log(topic, message.toString());
});


http.createServer(function (req, res) {

	var num = URL.parse(req.url, true).query['call'];
	if (num && !isNaN(parseInt(num)) ) {
		sendMessage(num);
	}
	res.end('ok:' + num);

}).listen(1884, '127.0.0.1');
console.log('create 1884 server')

//client.end();
