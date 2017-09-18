[![Build Status](https://travis-ci.org/kasonyang/netlib.svg?branch=master)](https://travis-ci.org/kasonyang/netlib)

# net-lib
A network library for java

# connect as a client

    ChannelHost host = ChannelHost.create();
    Channel client = host.createChannel();
    client.connect(new InetSocketAddress("localhost",80));

# listen as a server

    ChannelHost host = ChannelHost.create();
    host.createServerChannel(new InetSocketAddress("localhost",80), new AcceptHandler() {
      @Override
      public void accepted(Channel ch) {
        //TODO add you code here
      }
    });


