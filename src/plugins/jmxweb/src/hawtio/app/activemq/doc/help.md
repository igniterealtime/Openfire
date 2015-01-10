### ActiveMQ

Click [ActiveMQ](#/jmx/attributes?tab=activmemq) in the top navigation bar to see the ActiveMQ specific plugin. (The ActiveMQ tab won't appear if there is no broker in this JVM).  The ActiveMQ plugin works very much the same as the JMX plugin however with a focus on interacting with an ActiveMQ broker.

The tree view on the left-hand side shows the top level JMX tree of each broker instance running in the JVM.  Expanding the tree will show the various MBeans registered by ActiveMQ that you can inspect via the **Attributes** tab.

You can then click on the **Queue** node to see the queues and **Topic** node to see the topics. From either of these nodes you should see **Create Queue** or **Create Topic** tabs to be able to create new destinations.

Once you have selected a destination you should be able to **Send** to it, **Browse** a queue or view the  **Attributes** or **Charts**

You can also see a graphical view of all producers, destinations and consumers for all queues (or if you select a Topic folder then topics) using the **Diagram** tab. Selecting a single queue or topic shows just all the producers and consumers on that destination. This diagram makes it easy to spot if producers are sending messages when there are no consumers, or that consumers are on the wrong destination etc.
