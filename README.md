# Akka LPR Client

Does not use Port 721 - 732, instead auto-assign a higher port. Most Servers will just happily accept it.

## NOT PRODUCTION READY - USE AT YOUR OWN RISK

Always chunk and guess file size

Works against most Xerox WorkCentre and Fiery Controller

## TODO

Timeout/Failure if the TCP connection will be closed before we have 3 ACKs 