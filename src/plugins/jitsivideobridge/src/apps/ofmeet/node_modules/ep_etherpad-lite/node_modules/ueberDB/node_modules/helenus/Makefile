TEST_FILES=$(wildcard test/*.js)

test:
	NODE_PATH=lib/ node_modules/whiskey/bin/whiskey --real-time --scope-leaks --tests "$(TEST_FILES)"

test-cov:
	NODE_PATH=lib-cov/ node_modules/whiskey/bin/whiskey --real-time --scope-leaks --coverage --coverage-reporter html --coverage-dir test/coverage --coverage-no-instrument cassandra --tests "$(TEST_FILES)"
	rm -rf lib-cov

doc:
	rm -rf ./doc && node_modules/JSDoc/jsdoc -p -r ./lib -d ./doc

.PHONY: test test-cov doc