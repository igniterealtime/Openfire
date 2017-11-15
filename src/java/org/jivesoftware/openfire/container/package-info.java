/**
 * <p>The microkernel core of the server is a simple, flexible, nested
 * container framework defined in this package. </p>
 * <p> The container model consists of&nbsp; three primary participants:<br>
 * </p>
 * <ul>
 * <li><strong>Service</strong> - A well-known Java object defined by a
 * Java interface. Code running in the server should be organized into
 * services that perform logical (and limited) blocks of work. These
 * services can then be exposed as a Java interface and obtained using the
 * ServiceLookup service. Although most services will be local to the
 * container, some may use RMI, web services, XMPP, or other remote
 * procedure call technologies to provide services across the network.<br>
 * </li>
 * <li><strong>Module </strong>- The smallest server deployment unit. A
 * module has a well-defined life cycle that is managed by its hosting
 * container. A module may contain zero or more services and client code
 * that accesses these services. Modules can be deployed and configured
 * individually, allowing the easy implementation of server plugins and
 * on the fly reconfiguration of the server.<br>
 * </li>
 * <li><strong>Container </strong>- A special module that hosts server
 * modules including other containers. Hosting involves life cycle
 * management of child modules, configuration of child modules, and
 * providing access to shared resources. Containers also provide their own
 * configurable security managers and custom classloaders that extend the
 * classpath to automatically include module classes and jar files located
 * in well defined locations (e.g. similar to WEB-INF/lib and
 * WEB-INF/classes in a J2EE web-app/WAR).</li>
 * </ul>
 * <p>The nesting nature of containers allows a tree-like server
 * architecture with a root 'bootstrap container' with core modules, and
 * child containers with their own modules. Child containers by default
 * inherit and extend the bootstrap container's classpath and services
 * while being protected from implementation details of any modules,
 * containers, or services in layers above them. In some cases, child
 * containers may have restricted views or access to upper levels of the
 * tree (e.g. a user plug-in container that allows users to add arbitrary
 * server extensions).<br>
 * </p>
 */
package org.jivesoftware.openfire.container;
