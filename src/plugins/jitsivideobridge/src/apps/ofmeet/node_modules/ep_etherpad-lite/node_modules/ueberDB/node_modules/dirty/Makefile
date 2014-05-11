test:
	npm test

benchmark-v8:
	@find benchmark/v8/*.js | xargs -n 1 -t node

benchmark-php:
	@find benchmark/php/*.php | xargs -n 1 -t php

benchmark-dirty:
	@find benchmark/dirty/*.js | xargs -n 1 -t node

benchmark-all: benchmark-v8 benchmark-php benchmark-dirty
benchmark: benchmark-dirty

clean:
	@find . -name *.dirty | xargs rm

.PHONY: test benchmark
