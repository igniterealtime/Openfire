var fs = require('fs');

var should = require('should');

require('../minify.json.js');

describe('JSON.minify', function() {
    it('define', function() {
        JSON.minify.should.be.ok;
    });
    it('in-memory string', function() {
        var json = '{"key":"value"}';
        var res = JSON.minify(json);
        JSON.parse(res).key.should.equal('value');
    });
    it('comment.json', function() {
        var json = fs.readFileSync(__dirname + '/comment.json', 'utf8');
        var res = JSON.parse(JSON.minify(json));
        res.foo.should.equal('bar');
    });
    it('comment.json', function() {
        var json = fs.readFileSync(__dirname + '/plain.json', 'utf8');
        var res = JSON.parse(JSON.minify(json));
        res.foo.should.equal('bar');
    });
});
