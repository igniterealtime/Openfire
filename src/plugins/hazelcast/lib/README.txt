After you have licensed and downloaded Coherence EE from Oracle, place
the following jar files in this folder:

coherence.jar
coherence-work.jar

To build the clustering plugin, issue the following command from
the Openfire (source) /build/ folder:

$OPENFIRE_SRC/build> ant -Dplugin=clustering plugin

Also note that due to classpath loading order, it may be necessary to
either remove the coherence-cache-config.xml file from the Coherence
runtime JAR, or rename the plugin-clustering.jar file to force it to
load before coherence.jar (e.g. "clustering-plugin.jar" or similar).

In order to run Oracle Coherence in production mode, you will need to 
secure licensing for the Enterprise Edition (EE) of Coherence. While 
clustered caching for Openfire is available in the Standard Edition (SE), 
per the Oracle Fusion licensing docs the InvocationService (which is 
used by Openfire to distribute tasks among the cluster members) is only 
available in EE or Grid Edition (GE).

Note that Coherence is configured to run GE in development mode by default. 
You can change this setting by overriding the following Java system properties
via /etc/sysconfig/openfire (RPM) or openfired.vmoptions (Windows):

-Dtangosol.coherence.edition=EE
-Dtangosol.coherence.mode=prod

The current Coherence release is version 3.7.1.