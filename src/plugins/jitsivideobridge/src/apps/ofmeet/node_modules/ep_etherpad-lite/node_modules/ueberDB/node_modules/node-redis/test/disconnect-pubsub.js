var c = require('../').createClient()

c.on('error', console.error)

c.subscribe('channel')
c.psubscribe('pattern:*')
