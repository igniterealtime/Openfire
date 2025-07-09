.PHONY: all clean dbkg

all: build-openfire

# Can not use 'build' as target name, because there is a
# directory called build
build-openfire:
	./mvnw package --batch-mode --no-transfer-progress

test:
	./mvnw test --batch-mode --no-transfer-progress

clean:
	./mvnw clean

dist:
	./mvnw package --batch-mode --no-transfer-progress -DskipTests=true

plugins:
	./mvnw package --batch-mode --no-transfer-progress
