# TCP-Like-Protocol

Description of project

The TCP segments used in this project is 20 bytes headers plus payload. Total size of TCP segment is 100 bytes. In TCP header, I only use FIN bit in flag field and urgent data pointer is not used.

This TCP protocol can check bit errors and duplicate packet, and has timeout mechanism to deal with packet loss. Bit error is checked by checksum, which is calculated by wrapped around addition of all bytes in TCP segment. Timeout mechanism is implemented by calculating estimated RTT when an ACK comes. 

Running instruction

a)Run Receiver first, then run Sender. The syntax of invoking is same as assignment instruction. Here is invoking example:

Receiver: java Receiver recvfile.txt 41194 192.168.1.168 4119 stdout
Sender: java Sender transfile.txt 192.168.1.168 41192 4119 stdout 20

b)The sender is bind at port 41191, so when you invoke proxy, please set the source port as 41191 or listen to any port.

c)When invoking the sender, remote_IP and remote_port is proxy’s IP and listening port. So, at the receiver side, dest# of logfile is proxy’s listening port.
