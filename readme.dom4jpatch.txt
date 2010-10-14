This DOM4J patch against 1.6.1 improves the concurrency of the DocumentFactory class by removing the
synchronized keyword from the VERY commonly used getInstance() function. This was achieved by making
the 'instance' member variable final and assigning the value statically, as opposed to the first
time that getInstance() was called.

Rebuilding this was tricky, since the stock dom4j download doesn't build anymore. I had to get the DocumentFactory
class to compile by itself, and then replace the class file in the existing jar. This has been running in production
with tens of thousands of users for almost a year with no problems and greatly increased performance due to reduced
lock contention on the singleton.

To get even better performance out of this build of DOM4J, we have found that using the PerThreadSingleton strategy
helped quite a bit. This can be achieved by adding the following system property to the openfire JVM startup command:
-Dorg.dom4j.DocumentFactory.singleton.strategy=org.dom4j.util.PerThreadSingleton

This file should be deleted if this branch is merged back into trunk.
