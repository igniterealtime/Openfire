var simplesmtp = require("../index"),
    fs = require("fs");

// Example for http://tools.ietf.org/search/rfc1870

var maxMessageSize = 10;

var smtp = simplesmtp.createServer({
    maxSize: maxMessageSize, // maxSize must be set in order to support SIZE
    disableDNSValidation: true,
    debug: true
});
smtp.listen(25);

// Set up sender validation function
smtp.on("validateSender", function(connection, email, done){
    console.log(1, connection.messageSize, maxMessageSize)
    // SIZE value can be found from connection.messageSize
    if(connection.messageSize > maxMessageSize){
        var err = new Error("Max space reached");
        err.SMTPResponse = "452 This server can only accept messages up to " + maxMessageSize + " bytes";
        done(err);
    }else{
        done();
    }
});

// Set up recipient validation function
smtp.on("validateRecipient", function(connection, email, done){
    // Allow only messages up to 100 bytes
    if(connection.messageSize > 100){
        var err = new Error("Max space reached");
        err.SMTPResponse = "552 Channel size limit exceeded: " + email;
        done(err);
    }else{
        done();
    }
});

smtp.on("startData", function(connection){
    connection.messageSize = 0;
    connection.saveStream = fs.createWriteStream("/tmp/message.txt");
});

smtp.on("data", function(connection, chunk){
    connection.messageSize += chunk.length;
    connection.saveStream.write(chunk);
});

smtp.on("dataReady", function(connection, done){
    connection.saveStream.end();

    // check if message
    if(connection.messageSize > maxMessageSize){
        // mail was too big and therefore ignored
        var err = new Error("Max fileSize reached");
        err.SMTPResponse = "552 message exceeds fixed maximum message size";
        done(err);
    }else{
        done();
        console.log("Delivered message by " + connection.from +
            " to " + connection.to.join(", ") + ", sent from " + connection.host +
            " (" + connection.remoteAddress + ")");
    }
});