var config = {
    hosts: {
        domain: window.location.hostname,
        muc: 'conference.' + window.location.hostname, // FIXME: use XEP-0030
        bridge: 'jitsi-videobridge.' + window.location.hostname // FIXME: use XEP-0030
    },
    useIPv6: false, // ipv6 support. use at your own risk
    useNicks: false,
    useWebsockets: true,
    resolution: "360",
    bosh: window.location.protocol + "//" + window.location.host + '/http-bind/' // FIXME: use xep-0156 for that
};
