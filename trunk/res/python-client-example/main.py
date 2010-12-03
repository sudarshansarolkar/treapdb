from thrift import Thrift
from thrift.transport import TTransport
from thrift.transport import TSocket
from thrift.protocol.TBinaryProtocol import TBinaryProtocolAccelerated
from pytreap import TreapDBService
from pytreap.ttypes import *

def main():
    socket = TSocket.TSocket("localhost", 11811)
    transport = TTransport.TBufferedTransport(socket)
    protocol = TBinaryProtocol.TBinaryProtocolAccelerated(transport)
    client = TreapDBService.Client(protocol)
    transport.open()
    data = 'abc'*30
    for i in xrange(100):
        client.put(str(i),data)
    for i in xrange(10):
        print i,'=>',client.get(str(i))
    results = client.prefix('9',5) #at most 5 entries
    print results
    transport.close()
    
if __name__ == "__main__":
    main()

    