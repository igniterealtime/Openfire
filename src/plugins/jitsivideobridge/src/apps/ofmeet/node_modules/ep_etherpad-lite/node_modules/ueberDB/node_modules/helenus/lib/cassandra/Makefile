compile: Cassandra.js cassandra_types.js clean_compile

gen-nodejs:
	thrift --gen js:node cassandra.thrift

cassandra_types.js: gen-nodejs
	cp gen-nodejs/cassandra_types.js .
	patch -p1 < patches/cassandra_types.patch

Cassandra.js: gen-nodejs
	cp gen-nodejs/Cassandra.js .
	patch -p1 < patches/Cassandra.patch

clean_compile:
	rm -rf gen-nodejs

clean: clean_compile
	rm Cassandra.js cassandra_types.js

.PHONY: clean clean_compile
