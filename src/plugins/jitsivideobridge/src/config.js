var config = {
    hosts: {
        domain: window.location.hostname,
        muc: 'conference.' + window.location.hostname, // FIXME: use XEP-0030
        bridge: 'jitsi-videobridge.' + window.location.hostname // FIXME: use XEP-0030
    },
    useNicks: false,
    bosh: '/http-bind/' // FIXME: use xep-0156 for that
};
