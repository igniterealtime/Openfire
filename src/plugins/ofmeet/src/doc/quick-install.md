# Jitsi Meet quick install

This documents decribes the needed steps for quick Jitsi Meet installation on a Debian based GNU/Linux system.

N.B.: All commands are supposed to be run by root. If you are logged in as a regular user with sudo rights, please prepend ___sudo___ to each of the commands.

## Basic Jitsi Meet install

### Add the repository

```sh
add-apt-repository 'deb http://download.jitsi.org/nightly/deb unstable/'
wget -qO - https://download.jitsi.org/nightly/deb/unstable/archive.key | apt-key add -
```

add-apt-repository is in the default Ubuntu install and is available for both Ubuntu and Debian, but if it's not present, either install it with

```sh
apt-get -y install software-properties-common
add-apt-repository 'deb http://download.jitsi.org/nightly/deb unstable/'
wget -qO - https://download.jitsi.org/nightly/deb/unstable/archive.key | apt-key add -
```

or add the repository by hand with

```sh
echo 'deb http://download.jitsi.org/nightly/deb unstable/' >> /etc/apt/sources.list
wget -qO - https://download.jitsi.org/nightly/deb/unstable/archive.key | apt-key add -
```

### Update the package lists

```sh
apt-get update
```

### Install Jitsi Meet

```sh
apt-get -y install jitsi-meet
```

During the installation you'll be asked to enter the hostname of the Jitsi Meet instance. If you have a FQDN hostname for the instance already set ip in DNS, enter it there. If you don't have a resolvable hostname, you can enter the IP address of the machine (if it is static or doesn't change).

This hostname (or IP address) will be used for virtualhost configuration inside the Jitsi Meet and also you and your correspondents will be using it to access the web conferences.

### Open a conference

Launch a web broswer (Chrome, Chromium or latest Opera) and enter in the URL bar the hostname (or IP address) you used in the previous step.

Confirm that you trust the self-signed certificate of the newly installed Jitsi Meet.

Enjoy!

## Adding sip-gateway to Jitsi Meet

### Install Jigasi

```sh
apt-get -y install jigasi
```
or

```sh
wget https://download.jitsi.org/jigasi_1.0-1_amd64.deb
dpkg -i jigasi_1.0-1_amd64.deb
```

During the installation you'll be asked to enter your SIP account and password. This account will be used to invite the other SIP participants.

### Reload Jitsi Meet

Launch again a browser with the Jitsi Meet URL and you'll see a telephone icon on the right end of the toolbar. Use it to invite SIP accounts to join the current conference.

Enjoy!

## Troubleshoot

If the SIP gateway doesn't work on first try, restart it.

```sh
/etc/init.d/jigasi restart
```

## Deinstall

```sh
apt-get purge jigasi jitsi-meet jitsi-videobridge
```

Somethimes the following packages will fail to uninstall properly:

- jigasi
- jitsi-videobridge

When this happens, just run the deinstall command a second time and it should be ok.

The reason for failure is that not allways the daemons are stopped right away, there is a timeout before the actual stop. And if the unistall script goes on before the services' stop, there is an error.

The second run of the deinstall command fixes this, as by then the jigasi or jvb daemons are already stopped.
