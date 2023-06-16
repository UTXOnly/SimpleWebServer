#!/bin/sh

java -Dcom.sun.management.jmxremote \
     -Dcom.sun.management.jmxremote.port=8001 \
     -Dcom.sun.management.jmxremote.authenticate=false \
     -Dcom.sun.management.jmxremote.ssl=false \
     SimpleWebServer
     #SimpleAgent