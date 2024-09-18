.PHONY: all clean dbkg eclipse

all: build-openfire

# Can not use 'build' as target name, because there is a
# directory called build
build-openfire:
	./mvnw package --batch-mode --no-transfer-progress

clean:
	./mvnw clean

dist:
	./mvnw package --batch-mode --no-transfer-progress -DskipTests=true

plugins:
	./mvnw package --batch-mode --no-transfer-progress

eclipse: .settings .classpath .project

.settings:
	ln -s build/eclipse/settings .settings

.classpath:
	ln -s build/eclipse/classpath .classpath

.project:
	ln -s build/eclipse/project .project
