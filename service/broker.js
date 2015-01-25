/**
 * MQTT 的broker server
 */
var mosca = require('mosca')
var clientId = 0;
var PREFIX = '/client/';

var TOPIC = '/client/alarm';
var TOPIC_2 = '/client/alarm2';
var TEL = '15210838121';
var TIME = 600;

var ascoltatore = {
    //using ascoltatore
    type: 'mongo',
    url: 'mongodb://localhost:27017/mqtt',
    pubsubCollection: 'pushserver',
    mongo: {}
};

var settings = {
    port: 1883,
    backend: ascoltatore
};

var clientList = {};

var server = new mosca.Server(settings);

var message = {
  topic: '/client/notify',
  payload: 'abcdejkfjkdajf', // or a Buffer
  qos: 1, // 0, 1, or 2
  retain: true // or true
};

server.on('clientConnected', function(client) {
    console.log('client connected:', client.id);

    clientList[client.id] = null;
    delete clientList[client.id];
});

server.on('clientDisconnected', function(client) {
    console.log('Client Disconnected:', client.id);
    clientList[client.id] = client;
});


// fired when a message is received
server.on('published', function(packet, client) {
    //console.log('Published, packet:',packet);
    if (packet.topic === '/server/broad') {
        console.log('show server message: ', packet.payload); 
        broadMessage(packet.payload);
    }
});

server.on('ready', setup);

// fired when the mqtt server is ready
function setup() {
    console.log('Mosca server is up and running');

    setInterval(function () {
        sendMessage(TOPIC_2, TEL);
    }, 30000);
}

function sendMessage(topic, msg) {
    var message = {
        topic: topic,
        payload: msg, // or a Buffer
        qos: 1, // 0, 1, or 2
        retain: true // or true
    };
    //发送消息
    server.publish(message, function () {
       //TODO what ?  
       console.log('public :', message.payload);
    });
}

function getClientTopic(clientId) {
   var topic = PREFIX + clientId;
   return topic;
}

/**
 * 广播给所有在线的客户
 */
function broadMessage(msg) {
	sendMessage(TOPIC, msg);
}
