title=Netlib
date=2017-10-11
type=post
tags=
status=published
~~~~~~

# Overview
Netlib is a async socket framework library for java.

# Installation

gradle:

    compile 'site.kason:netlib:VERSION'


# Usage

## Connect as a client

    ChannelHost host = ChannelHost.create();
    Channel client = host.createChannel();
    client.connect(new InetSocketAddress("localhost",80));
    host.listen();

## Listen as a server

    ChannelHost host = ChannelHost.create();
    host.createServerChannel(new InetSocketAddress("localhost",80), new AcceptHandler() {
      @Override
      public void accepted(Channel ch) {
        //TODO add you code here
      }
    });
    host.listen();

## Transfer data

### write

    byte[] data = "Hello,world!".getBytes();
    WriteTask task = new ByteWriteTask(data);
    channel.write(task);
    
### read

    channel.read(new ReadTask() {
    
      @Override
      public boolean handleRead(Channel channel,IOBuffer buffer) {
        int len = buffer.getReadableSize();
        log("client:read " + len + " bytes");
        byte[] receivedData = new byte[len];
        buffer.poll(receivedData);
        //process receivedData
        return true;//return true if the task is finished
      }
      
    });

## Enable SSL

    boolean clientMode = true;
    String keyStore = "sslclientkeys";
    String pwd = "net-lib";
    SSLContext context = SSLContextFactory.create(keyStore, pwd);
    SSLCodec sslCodec = new SSLCodec(ch,context, clientMode);
    channel.addCodec(sslCodec);

## Handle exceptions

    ExceptionHandler handler = new ExceptionHandler() {
      @Override
      public void handleException(Channel ch, Exception ex) {
        ex.printStack();
        ch.close();
      }
    };
    client.setExceptionHandler(handler);



