package org.jivesoftware.openfire.mediaproxy;

import org.jivesoftware.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.*;

/**
 * A media proxy session enables two clients to exchange UDP traffic. Each client connects to
 * a UDP port and then the proxy is responsible for exchanging traffic. Each session uses
 * a total of four ports: two for traffic exchange, and two control ports.
 *
 * @author Thiago Camargo
 */
public abstract class MediaProxySession extends Thread implements ProxyCandidate, DatagramListener {

    private List<SessionListener> sessionListeners = new ArrayList<SessionListener>();

    private String id;
    private String pass;
    private String creator = "";
    private long timestamp = 0;

    protected InetAddress localAddress;
    protected InetAddress hostA;
    protected InetAddress hostB;

    protected int portA;
    protected int portB;

    protected int localPortA;
    protected int localPortB;

    protected DatagramSocket socketA;
    protected DatagramSocket socketAControl;
    protected DatagramSocket socketB;
    protected DatagramSocket socketBControl;

    protected Channel channelAtoB;
    protected Channel channelAtoBControl;
    protected Channel channelBtoA;
    protected Channel channelBtoAControl;

    protected Thread threadAtoB;
    protected Thread threadAtoBControl;
    protected Thread threadBtoA;
    protected Thread threadBtoAControl;

    private Timer idleTimer = null;
    private Timer lifeTimer = null;

    private int minPort = 10000;
    private int maxPort = 20000;

    /**
     * Creates a new static UDP channel between Host A and Host B.
     *
     * @param id           of the Session (Could be a Jingle session ID)
     * @param creator      the session creator name or description
     * @param localAddress the localhost IP that will listen for UDP packets
     * @param hostA        the hostname or IP of the point A of the Channel
     * @param portA        the port number point A of the Channel
     * @param hostB        the hostname or IP of the point B of the Channel
     * @param portB        the port number point B of the Channel
     * @param minPort      the minimal port value to be used by the server
     * @param maxPort      the maximun port value to be used by the server
     */
    public MediaProxySession(String id, String creator, String localAddress, String hostA, int portA, String hostB,
                             int portB, int minPort, int maxPort) {
        this.id = id;
        this.creator = creator;
        this.minPort = minPort;
        this.maxPort = maxPort;
        this.pass = String.valueOf(Math.abs(new Random().nextLong()));
        try {
            this.hostA = InetAddress.getByName(hostA);
            this.hostB = InetAddress.getByName(hostB);

            this.portA = portA;
            this.portB = portB;

            this.localAddress = InetAddress.getByName(localAddress);
            this.localPortA = getFreePort();
            this.socketA = new DatagramSocket(localPortA, this.localAddress);
            this.socketAControl = new DatagramSocket(localPortA + 1, this.localAddress);
            this.localPortB = getFreePort();
            this.socketB = new DatagramSocket(localPortB, this.localAddress);
            this.socketBControl = new DatagramSocket(localPortB + 1, this.localAddress);
            if (Log.isDebugEnabled()) {
                Log.debug("MediaProxySession: Session Created at: A " + localPortA + " : B " + localPortB);
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
    }

    /**
     * Obtain a free port with a nested control port we can use.
     *
     * @return A free port number.
     */
    protected int getFreePort() {
        ServerSocket ss;
        int freePort = 0;
        int controlPort;

        for (int i = 0; i < 10; i++) {
            freePort = (int) (minPort + Math.round(Math.random() * (maxPort - minPort)));
            freePort = freePort % 2 == 0 ? freePort : freePort + 1;
            try {
                ss = new ServerSocket(freePort);
                freePort = ss.getLocalPort();
                ss.close();
                ss = new ServerSocket(freePort + 1);
                controlPort = ss.getLocalPort();
                ss.close();
                if (controlPort == (freePort + 1))
                    return freePort;
            }
            catch (IOException e) {
                Log.error(e);
            }
        }
        try {
            ss = new ServerSocket(0);
            freePort = ss.getLocalPort();
            ss.close();
        }
        catch (IOException e) {
            Log.error(e);
        } finally {
            ss = null;
        }
        return freePort;
    }

    /**
     * Get the ID of the Session
     *
     * @return the ID of the session
     */
    public String getSID() {
        return id;
    }

    /**
     * Get the pass of this Session
     * A pass can be used to authorize an Session modification
     */
    public String getPass() {
        return pass;
    }

    /**
     * Get the agent creator.
     * This field is open to MediaProxy users and just can be set in constructor.
     *
     * @return the session creator name or description
     */
    public String getCreator() {
        return creator;
    }

    /**
     * Get last packet arrived timestamp
     *
     * @return TimeStamp in Millis
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Thread override method
     */
    public void run() {
        // Create channels for parties
        createChannels();

        // Start a thread for each channel
        threadAtoB = new Thread(channelAtoB);
        threadAtoBControl = new Thread(channelAtoBControl);
        threadBtoA = new Thread(channelBtoA);
        threadBtoAControl = new Thread(channelBtoAControl);

        threadAtoB.start();
        threadAtoBControl.start();
        threadBtoA.start();
        threadBtoAControl.start();

        // Listen to channel events
        addChannelListeners();
    }

    /**
     * Creates 4 new channels for the two entities. We will create a channel between A and B and vice versa
     * and also a control channel betwwen A and B and vice versa.
     */
    abstract void createChannels();

    /**
     * Adds listener to channel events like receiving data.
     */
    void addChannelListeners() {
        channelAtoB.addListener(this);
        channelAtoBControl.addListener(this);
        channelBtoA.addListener(this);
        channelBtoAControl.addListener(this);
    }

    /**
     * Stop the Session
     */
    public void stopAgent() {

        try {
            if (idleTimer != null) {
                idleTimer.cancel();
                idleTimer.purge();
                idleTimer = null;
            }
        } catch (Exception e) {
            Log.error(e);
        }

        try {
            if (lifeTimer != null) {
                lifeTimer.cancel();
                lifeTimer.purge();
                lifeTimer = null;
            }
        } catch (Exception e) {
            Log.error(e);
        }

        channelAtoB.removeListeners();
        channelAtoBControl.removeListeners();
        channelBtoA.removeListeners();
        channelBtoAControl.removeListeners();

        try {
            channelAtoB.cancel();
            channelAtoBControl.cancel();
            channelBtoA.cancel();
            channelBtoAControl.cancel();
        } catch (Exception e) {
            Log.error(e);
        }

        socketA.close();
        socketAControl.close();
        socketB.close();
        socketBControl.close();

        dispatchAgentStopped();

        Log.debug("MediaProxySession: Session Stopped");
    }

    /**
     * Get localhost of the Session
     *
     * @return the localhost of the session
     */
    public InetAddress getLocalhost() {
        return localAddress;
    }

    /**
     * Get the Host A IP
     *
     * @return the host A ip
     */
    public InetAddress getHostA() {
        return hostA;
    }

    /**
     * Get the Host B IP
     *
     * @return the host B ip
     */
    public InetAddress getHostB() {
        return hostB;
    }

    /**
     * Set port A value
     *
     * @param portA the port number for A
     */
    public void setPortA(int portA) {
        if (Log.isDebugEnabled()) {
            Log.debug("MediaProxySession: PORT CHANGED(A):" + portA);
        }
        this.portA = portA;
    }

    /**
     * Set port B value
     *
     * @param portB the port number for B
     */
    public void setPortB(int portB) {
        if (Log.isDebugEnabled()) {
            Log.debug("MediaProxySession: PORT CHANGED(B):" + portB);
        }
        this.portB = portB;
    }

    /**
     * Set the Host A IP
     *
     * @param hostA the host for A
     */
    public void setHostA(InetAddress hostA) {
        this.hostA = hostA;
    }

    /**
     * Set the Host B IP
     *
     * @param hostB the host for B
     */
    public void setHostB(InetAddress hostB) {
        this.hostB = hostB;
    }

    /**
     * Get the Port A IP
     *
     * @return the port for A
     */
    public int getPortA() {
        return portA;
    }

    /**
     * Get the Port B IP
     *
     * @return the port for B
     */
    public int getPortB() {
        return portB;
    }

    /**
     * Get the localport that listen for Host A Packets
     *
     * @return the local port for A
     */
    public int getLocalPortA() {
        return localPortA;
    }

    /**
     * Get the localport that listen for Host B Packets
     *
     * @return the local port for B
     */
    public int getLocalPortB() {
        return localPortB;
    }

    public void sendFromPortA(String host, int port) {
        try {
            InetAddress address = InetAddress.getByName(host);
            channelAtoB.setHost(address);
            channelAtoB.setPort(port);
            channelAtoBControl.setHost(address);
            channelAtoBControl.setPort(port + 1);
        }
        catch (Exception e) {
            Log.error(e);
        }
    }

    public void sendFromPortB(String host, int port) {
        try {
            InetAddress address = InetAddress.getByName(host);
            channelBtoA.setHost(address);
            channelBtoA.setPort(port);
            channelBtoAControl.setHost(address);
            channelBtoAControl.setPort(port + 1);
        }
        catch (Exception e) {
            Log.error(e);
        }
    }

    /**
     * Implement DatagramListener to timestamp last packet arrived
     *
     * @param datagramPacket
     */
    public boolean datagramReceived(DatagramPacket datagramPacket) {
        timestamp = System.currentTimeMillis();
        return true;
    }

    /**
     * Add a keep alive detector.
     * If the packet still more than the keep alive delay without receiving any packets. The Session is
     * stoped and remove from agents List.
     *
     * @param delay delay time in millis to check if the channel is inactive
     */
    void addKeepAlive(long delay) {
        if (idleTimer != null) return;
        idleTimer = new Timer();
        idleTimer.scheduleAtFixedRate(new TimerTask() {
            long lastTimeStamp = getTimestamp();

            public void run() {
                if (lastTimeStamp == getTimestamp()) {
                    stopAgent();
                    return;
                }
                lastTimeStamp = getTimestamp();
            }
        }, delay, delay);
    }

    /**
     * Add a limited life time to the Session.
     * The Session is stoped and remove from agents List after a certain time.
     * Prevents that network cycles, refreshes a Session forever.
     *
     * @param lifetime time in Seconds to kill the Session
     */
    void addLifeTime(long lifetime) {
        lifetime *= 1000;
        if (lifeTimer != null) return;
        lifeTimer = new Timer();
        lifeTimer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                stopAgent();
            }
        }, lifetime, lifetime);
    }

    /**
     * Adds a listener for Session events
     *
     * @param sessionListener
     */
    public void addAgentListener(SessionListener sessionListener) {
        sessionListeners.add(sessionListener);
    }

    /**
     * Removes an Session events listener
     *
     * @param sessionListener
     */
    public void removeAgentListener(SessionListener sessionListener) {
        sessionListeners.remove(sessionListener);
    }

    /**
     * Removes every Session events listeners
     */
    public void clearAgentListeners() {
        sessionListeners.clear();
    }

    /**
     * Dispatch Stop Event
     */
    public void dispatchAgentStopped() {
        for (SessionListener sessionListener : sessionListeners) {
            try {
                sessionListener.sessionClosed(this);
            } catch (Exception e) {
                Log.error(e);
            }
        }
    }
}