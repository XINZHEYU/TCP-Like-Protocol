
import java.io.*;
import java.net.*;
import java.sql.Timestamp;
import java.util.Random;



public class Sender {
	
	public static boolean close = false;
	public static boolean timerstop = false;
	private static boolean timerstate = false;
	public static int winsize;
	public static File file;
	public static int ackportnum;
	public static boolean ackstate = false;
	
	public Sender(){
	}

	public static void main(String args[])throws IOException, InterruptedException {
		
		String transfile = args[0];
		String remoteip = args[1];
		int remoteport = Integer.parseInt(args[2]);
		ackportnum = Integer.parseInt(args[3]);
		String logfilename = args[4];
		int winsize = Integer.parseInt(args[5]);
        
		ReceiveACK receiveack = new ReceiveACK(ackportnum);                 //start the receive ack thread
		Thread thread = new Thread(receiveack);
		thread.start();
        
		double estimatedRTT = 100;                                          //set timeout
		Timeout timeout = new Timeout(estimatedRTT);
		
        int byteperpkt = 100;                                               //set bytes number in a packet
		
        DatagramSocket sender = new DatagramSocket(41191);                  //create udp socket
		if(sender.isConnected()){
			System.out.println("sender is start");
		}
		DatagramPacket send;
		System.out.println("receiver connected");
		file = new File(transfile);
		try{
			FileOperate originfile;
			originfile = new FileOperate(file);
			File lfile = new File(logfilename);
			lfile.delete();
			lfile.createNewFile();
			FileOperate lgfile = new FileOperate(lfile);
			boolean stdout;
			if(logfilename.equals("stdout")){
                System.out.println("TIMESTAMP " + "SOURCE " + "DEST " + "SEQNUMBER " + "ACKNUMBER " + "FIN " + "ESTIMATED_RTT");
				stdout = true;
				lfile.delete();
			}
			else{
				stdout = false;
				lgfile.logfile("TIMESTAMP" , "SOURCE" , "DEST" , "SEQNUMBER" , "ACKNUMBER" , "FIN" , "ESTIMATED_RTT");
			}
			int count = (int) Math.ceil(file.length() / (double)(byteperpkt - 20)) - 1;
			int ackedpkt = -1;
			int winbase = 0;
			int lastsendpkt = -1;
			PacketInfo[] packetinfo = new PacketInfo[count + 1];
			for (int i = 0 ; i < count + 1 ; i++){
				packetinfo[i] = new PacketInfo(0 , 0 , 0);
			}
			TCPHeader senddata;
			while(true){
                
                //piplining send packet
                
				if(lastsendpkt - winbase < winsize - 1 && lastsendpkt < count){
					senddata = new TCPHeader(originfile.splitfile(file, byteperpkt - 20 , (++lastsendpkt)).getBytes());
					byte[] sendpkt = senddata.addheader(41191 , remoteport , lastsendpkt , false , senddata.length() + 20);
					TCPHeader segment = new TCPHeader(sendpkt);
					send = new DatagramPacket(sendpkt , sendpkt.length , InetAddress.getByName(remoteip) , remoteport);
					sender.send(send);
					if(stdout)
						System.out.println(new Timestamp(System.currentTimeMillis()).toString() + ", " + segment.getsource()
						+ ", " + segment.getdest() + ", " + lastsendpkt + ", " + "0" + ", " + segment.getFIN() + ", " + String.valueOf(estimatedRTT));
					else
						lgfile.logfile(new Timestamp(System.currentTimeMillis()).toString() , segment.getsource() 
						, segment.getdest() , Integer.toString(lastsendpkt) , "0" , segment.getFIN() , String.valueOf(estimatedRTT));
					packetinfo[lastsendpkt].setsendtime();
					packetinfo[lastsendpkt].settranstimes();
				}
                
                //start timer
                
				if(!timerstate){
					timerstop = false;
					Thread.sleep(10);
					new Thread(timeout).start();
					timerstate = true;
				}
                
                //receive ack
                
				if(ReceiveACK.getack() >= winbase && ReceiveACK.getack() <= winbase + winsize - 1){
					ackedpkt = ReceiveACK.getack();
					winbase = ackedpkt + 1;
					for(int i = ackedpkt ; i >= 0 ; i--){
						if(packetinfo[i].getacktime() == 0)
							packetinfo[i].setacktime();
					}
					int samplepkt = new Random().nextInt(ackedpkt + 1);
					estimatedRTT = new RTTCalculate(estimatedRTT , packetinfo[samplepkt].getsendtime() , packetinfo[samplepkt].getacktime()).setRTT();
					timerstop = true;
					timerstate = false;
					Thread.sleep(10);
					if(lastsendpkt > winbase){
						timerstop = false;
						Thread.sleep(10);
						new Thread(timeout).start();
						timerstate = true;
					}
				}
                
                //sending accomplish
                
				if(ackedpkt == count){
					timerstop = true;
					timerstate = false;
					break;
				}
				Thread.sleep(10);
				
                //timeout
                
				if(Timeout.gettimeout()){
					lastsendpkt = ackedpkt + 1;
					senddata = new TCPHeader(originfile.splitfile(file, byteperpkt - 20 , lastsendpkt).getBytes());
					byte[] sendpkt = senddata.addheader(41191 , remoteport , lastsendpkt , false , senddata.length() + 20);
					TCPHeader segment = new TCPHeader(sendpkt);
					send = new DatagramPacket(sendpkt , sendpkt.length , InetAddress.getByName(remoteip) , remoteport);
					sender.send(send);
					if(stdout)
                        System.out.println(new Timestamp(System.currentTimeMillis()).toString() + ", " + segment.getsource()+ ", " + segment.getdest() + ", " + lastsendpkt + ", " + "0" + ", " + segment.getFIN() + ", " + String.valueOf(estimatedRTT));
					else
						lgfile.logfile(new Timestamp(System.currentTimeMillis()).toString() , segment.getsource() 
						, segment.getdest() , Integer.toString(lastsendpkt) , "0" , segment.getFIN() , String.valueOf(estimatedRTT));
					packetinfo[lastsendpkt].setsendtime();
					packetinfo[lastsendpkt].settranstimes();
					timerstop = true;
					timerstate = false;
					Thread.sleep(10);
					timerstop = false;
					Thread.sleep(10);
					new Thread(timeout).start();
					timerstate = true;
				}
				
				
			}
            
            //output delivery information
			
			System.out.println("Delivery completed successfully.");
			System.out.println("Total bytes sent: " + file.length());
			System.out.println("Segments sent: " + (count + 1));
			int retransmittimes = 0;
			for(int i = 0 ; i < count ; i++){
				if(packetinfo[i].gettranstimes() != 1)
					retransmittimes += (packetinfo[i].gettranstimes() - 1);
			}
			System.out.println("segments Retransmitted: " + retransmittimes);
            
            //send FIN
			
			senddata = new TCPHeader((" ").getBytes());
			byte[] sendpkt = senddata.addheader(41191 , remoteport , 0 , true , byteperpkt);
			send = new DatagramPacket(sendpkt , byteperpkt , InetAddress.getByName(remoteip) , remoteport);
			sender.send(send);
			estimatedRTT = 100;
			timerstop = false;
			Thread.sleep(10);
			new Thread(timeout).start();
			timerstate = true;
			while(true){
				if(Timeout.gettimeout()){
					senddata = new TCPHeader((" ").getBytes());
					sendpkt = senddata.addheader(41191 , remoteport , 0 , true , byteperpkt);
					send = new DatagramPacket(sendpkt , byteperpkt , InetAddress.getByName(remoteip) , remoteport);
					sender.send(send);
					timerstop = true;
					timerstate = false;
					Thread.sleep(10);
					timerstop = false;
					Thread.sleep(10);
					new Thread(timeout).start();
					timerstate = true;
				}
				Thread.sleep(10);
				if(ReceiveACK.getackfin()){
					senddata = new TCPHeader(("ACKclose").getBytes());
					sendpkt = senddata.addheader(41191 , remoteport , 0 , false , byteperpkt);
					send = new DatagramPacket(sendpkt , byteperpkt , InetAddress.getByName(remoteip) , remoteport);
					sender.send(send);
					break;
				}
			}
			
		}
		catch(Exception e){
			System.out.println("error: " + e);
		}
		finally{
			System.out.println("close the sender");
			sender.close();
			close = true;
			timerstop = true;
			timerstate = true;
			System.exit(0);
		}
	}
	
	public static boolean gettimestop(){
		return timerstop;
	}
	
	public int getwinsize(){
		return winsize;
	}
	
	public static boolean getclose(){
		return close;
	}
	
	public static File getfile(){
		return file;
	}
}

class Timeout implements Runnable{
	private double timer;
	private static boolean timeout;
	
    public Timeout(double miliseconds) {
        timer = miliseconds;
    }
    
    public Timeout(){
    	
    }
    
    
    public void run() {
        try {
        	timeout = false;
        	long n = 0;
        	while(true){
        		Thread.sleep(1);
        		if(Sender.gettimestop())
        			break;
        		if(n == timer){
            		timeout = true;
            		break;
        		}
        		n++;
        	}
        }
        catch (InterruptedException e) {
			e.printStackTrace();
		}
    }

	public static boolean gettimeout() {
		return timeout;
	}
    
   
}

class ReceiveACK extends Thread implements Runnable{
	
	private int port;
	public static boolean ackFIN = false;
	public static boolean FIN = false;
	public static boolean recvconnect = false;
	private static boolean ackfinstate;
	private static int ack = -1;
	
	public ReceiveACK(int port){
		this.port = port;
	}
	
	public ReceiveACK(boolean ack){
	}

	public void run(){
		
		
		try{
			@SuppressWarnings("resource")
			ServerSocket r = new ServerSocket(port);
			Socket recvack = r.accept();
			recvconnect = true;
			BufferedReader receive = new BufferedReader(new InputStreamReader(recvack.getInputStream()));
			new Sender();
			while(!Sender.getclose()){
				String recv = receive.readLine();
				if(recv.equals("ACKclose")){
					ackFIN = true;
				}
				else if(recv.equals("FIN")){
					FIN = true;
				}
				else
					ack = Integer.parseInt(recv.substring(3));
				
			}

		}
		catch (Exception e) {
			System.out.println("error: " + e);
		}
		
		
	}
	
	public static boolean getackfin(){
		if(ackFIN && FIN){
			ackfinstate = true;
			return ackfinstate;
		}
		else{
			ackfinstate = false;
			return ackfinstate;
		}
	}
	
	public static int getack(){
		return ack;
	}
}

class RTTCalculate {
	
	private double estimatedRTT;
	private long sendtime;
	private long acktime;
	private double devRTT = 0;
	
	public RTTCalculate(double RTT , long stime , long atime){
		estimatedRTT = RTT;
		sendtime = stime;
		acktime = atime;
	}
	
	public double setRTT(){
		int sampleRTT = (int)(acktime - sendtime);
		estimatedRTT = estimatedRTT * 0.875 + sampleRTT * 0.125;
		devRTT = 0.75 * devRTT + 0.25 * Math.abs(estimatedRTT - sampleRTT);
		double timeoutinterval = estimatedRTT + devRTT;
		return timeoutinterval;
	}
}

class PacketInfo {
	private long sendtime;
	private long acktime;
	private int transtimes;
	
	public PacketInfo(int ttimes , long stime , long atime){
		sendtime = stime;
		acktime = atime;
		transtimes = ttimes;
	}
	
	public void settranstimes(){
		transtimes++;
	}
	
	public int gettranstimes(){
		return transtimes;
	}
	
	public long setsendtime(){
		sendtime = new Timestamp(System.currentTimeMillis()).getTime();
		return sendtime;
	}
	
	public long setacktime(){
		acktime = new Timestamp(System.currentTimeMillis()).getTime();
		return acktime;
	}
	
	public long getsendtime(){
		return sendtime;
	}
	public long getacktime(){
		return acktime;
	}
}

class FileOperate {
	private File targetfile;
	
	public FileOperate(File file){
		this.targetfile = file;
	}
	
	public String splitfile(File originfile , int byteperpkt , int pktnumber) throws IOException{
		@SuppressWarnings("resource")
		RandomAccessFile rfile = new RandomAccessFile(targetfile , "r");
		rfile.seek(byteperpkt * pktnumber);
		long bytenumber = rfile.length();
		byte[] buffer;
		if(bytenumber < byteperpkt)
			buffer = new byte[(int)bytenumber];
		else
			buffer = new byte[byteperpkt];
		rfile.read(buffer);
		String packet = new String(buffer);
		return packet;
	}
	
	public void mergefile(String packet) throws IOException{
		@SuppressWarnings("resource")
		RandomAccessFile wfile = new RandomAccessFile(targetfile , "rw");
		byte[] buffer = packet.getBytes();
		wfile.seek(targetfile.length());
		wfile.write(buffer);
	}
	
	public void logfile(String timestamp , String source , String dest , String seqnumber , String ack , String FIN , String estimatedRTT) throws IOException{
		String wbuf = timestamp + " " + source + " " + dest + " " + seqnumber + " " + ack + " " + FIN + " " + estimatedRTT;
		@SuppressWarnings("resource")
		RandomAccessFile logfile = new RandomAccessFile(targetfile , "rw");
		logfile.seek(targetfile.length());
		logfile.writeBytes(wbuf);
		logfile.writeBytes("\r\n");
	}
	
	
}

class TCPHeader {
	private byte[] data;
	
	public TCPHeader(byte[] d){
		this.data = d;
	}
	
	public int length(){
		return data.length;
	}
	public byte[] addheader(int srport , int destport , int seqnumber , boolean FIN , int byteperpkt){
		byte[] tcpheader = new byte[20];
		byte[] tcpsegment = new byte[byteperpkt];
		byte[] sourceport = new byte[2];
		byte[] destinationport = new byte[2];
		byte[] seq_number = new byte[4];
		byte[] acknumber = new byte[4];
		byte[] headlen = new byte[]{80};
		byte[] flag = new byte[1];
		if(FIN){
			flag[0] = 1;
		}
		byte[] recvwin = new byte[2];
		byte[] checksum = new byte[2];
		byte[] urgpointer = new byte[2];
		
		
		sourceport = inttobyte(srport , 2);
		destinationport = inttobyte(destport , 2);
		seq_number = inttobyte(seqnumber , 4);

		System.arraycopy(sourceport, 0, tcpheader, 0, 2);
		System.arraycopy(destinationport, 0, tcpheader, 2, 2);
		System.arraycopy(seq_number, 0, tcpheader, 4, 4);
		System.arraycopy(acknumber, 0, tcpheader, 8, 4);
		System.arraycopy(headlen, 0, tcpheader, 12, 1);
		System.arraycopy(flag, 0, tcpheader, 13, 1);
		System.arraycopy(recvwin, 0, tcpheader, 14, 2);
		System.arraycopy(checksum, 0, tcpheader, 16, 2);
		System.arraycopy(urgpointer, 0, tcpheader, 18, 2);
		System.arraycopy(tcpheader, 0, tcpsegment, 0, 20);
		System.arraycopy(data, 0, tcpsegment, 20, data.length);
		
		checksum = checksum(tcpsegment);
		System.arraycopy(checksum, 0, tcpsegment, 16, 2);
		
		return tcpsegment;

	}
	
	public void deliver(File file) throws IOException{
		byte[] recvdata = new byte[data.length - 20];
		System.arraycopy(data, 20, recvdata, 0, data.length - 20);
		String recvstr = new String(recvdata);
		mergefile(recvstr , file);
	}
	
	public void mergefile(String packet , File file) throws IOException{
		@SuppressWarnings("resource")
		RandomAccessFile wfile = new RandomAccessFile(file , "rw");
		byte[] buffer = packet.getBytes();
		wfile.seek(file.length());
		wfile.write(buffer);
	}
	
	public String deleteheader(){
		byte[] recvdata = new byte[data.length - 20];
		System.arraycopy(data, 20, recvdata, 0, data.length - 20);
		String recvstr = new String(recvdata);
		return recvstr;
	}
	
	public boolean checkFIN(){
		if(data[13] == 1){
			return true;
		}
		return false;
	}
	
	public String getFIN(){
		if(data[13] == 1)
			return "1";
		else
			return "0";
	}
	
	public int checkseqnumber(){
		int seqnumber; 
		seqnumber = data[7] & 0xFF | (data[6] & 0xFF) << 8 | (data[5] & 0xFF) << 16 | (data[4] & 0xFF) << 24; 
	    return seqnumber;
	}
	
	public byte[] checksum(byte[] d){
		byte[] result = new byte[2];
		String checksum = "0000000000000000";
		for(int i = 0 ; i < d.length ; i = i+2){
			String m = bytetobstr(d[i]) + bytetobstr(d[i+1]);
			checksum  = add(checksum , m);
		}
		checksum = Integer.toBinaryString(Integer.valueOf(checksum, 2) ^ 65535);
		if(checksum.length() < 16){
			for(int i = checksum.length() ; i < 16 ; i++){
				checksum = "0" + checksum;
			}
		}
		result[0] = Integer.valueOf(checksum.substring(0, 8), 2).byteValue();
		result[1] = Integer.valueOf(checksum.substring(8), 2).byteValue();
		
		return result;
	}
	
	public boolean checkcorrupt(){
		String result = "0000000000000000";
		for(int i = 0 ; i < data.length ; i = i+2){
			String x = bytetobstr(data[i]) + bytetobstr(data[i+1]);
			result = add(result , x);
		}
		if(result.equals("1111111111111111")){
			return true;
		}
		else
			return false;
	}
	
	public String getsource(){
		String source = bytetobstr(data[0]) + bytetobstr(data[1]);
		source = String.valueOf(Integer.valueOf(source , 2));
		return source;
	}
	
	public String getdest(){
		String dest = bytetobstr(data[2]) + bytetobstr(data[3]);
		dest = String.valueOf(Integer.valueOf(dest , 2));
		return dest;
	}
	
	public byte[] inttobyte(int integer , int bytesize){
		byte[] a = new byte[bytesize];
		String intstr = Integer.toBinaryString(integer);
		if(intstr.length() < bytesize * 8){
			for(int i = intstr.length() ; i < bytesize * 8 ; i++){
				intstr = "0" + intstr;
			}
		}
		for (int i = 0; i < bytesize - 1; i++){  
			a[i] = Integer.valueOf(intstr.substring(8 * i, 8 * i + 8), 2).byteValue();     
		}
		a[bytesize - 1] = Integer.valueOf(intstr.substring(8 * (bytesize - 1)), 2).byteValue();
		return a;
	}
	
	public String bytetobstr(byte b){
		String ZERO = "00000000";
		String s = Integer.toBinaryString(b);
        if (s.length() > 8) {
            s = s.substring(s.length() - 8);
        } else if (s.length() < 8) {
            s = ZERO.substring(s.length()) + s;
        }
        return s;
            
	}
	
	private String add(String x , String y){
		int x1 = Integer.valueOf(x,2);
		int y1 = Integer.valueOf(y,2);
		int z = x1 + y1;
		String result = Integer.toBinaryString(z);
		if(result.length() < 16){
			for(int i = result.length() ; i < 16 ; i++){
				result = "0" + result;
			}
			return result;
		}
		if(result.length() == 16)
			return result;
		if(result.length() > x.length() && result.substring(0 , 1).equals("1")){
			result = Integer.toBinaryString(Integer.valueOf(result.substring(1) , 2) + 1);
		}
		if(result.length() < 16){
			for(int i = result.length() ; i < 16 ; i++){
				result = "0" + result;
			}
		}
		return result;

	}
}
