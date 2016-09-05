import java.io.*;
import java.net.*;
import java.sql.Timestamp;

public class Receiver {
	
	public static File file;
	public static String senderip;
	public static int senderport;

	public static void main(String[] args)throws IOException {
		
		String recvfile = args[0];
		int listenport = Integer.parseInt(args[1]);
		senderip = args[2];
		senderport = Integer.parseInt(args[3]);
		String logfile = args[4];
		
		int byteperpkt = 100;
		TCPHeader2[] recvbuf = new TCPHeader2[50];
		Socket sendack;
		PrintStream out;
		DatagramSocket receiver = new DatagramSocket(listenport);
		System.out.println("Receiver start.");
		byte[] recvpkt = new byte[byteperpkt];
		DatagramPacket recv = new DatagramPacket(recvpkt , 0 , recvpkt.length);
		file = new File(recvfile);
		try{
			file.delete();
			file.createNewFile();
			File lfile = new File(logfile);
			lfile.delete();
			lfile.createNewFile();
			FileOperate2 lgfile = new FileOperate2(lfile);
			int lastackedpkt = -1;
			int recvseq;
			boolean stdout;
			if(logfile.equals("stdout")){
                System.out.println("TIMESTAMP " + "SOURCE " + "DEST " + "SEQNUMBER " + "ACKNUMBER " + "FIN");
				stdout = true;
				lfile.delete();
			}
			else{
				lgfile.logfile("TIMESTAMP" , "SOURCE" , "DEST" , "SEQNUMBER" , "ACKNUMBER" , "FIN");
				stdout = false;
			}

			receiver.receive(recv);
			sendack = new Socket(senderip , senderport);
			out = new PrintStream(sendack.getOutputStream());
			
			while(true){
				receiver.receive(recv);
				TCPHeader2 recvdata = new TCPHeader2(recvpkt);
				Thread.sleep(10);
				if(!recvdata.checkcorrupt()){                   //check bit error
					out.println("ACK" + lastackedpkt);
					continue;
				}
				if(recvdata.checkFIN()){                        //check FIN
					break;
				}
				
				Thread.sleep(10);
				recvseq = recvdata.checkseqnumber();            //calculate sequence number
				
				if(stdout)
					System.out.println(new Timestamp(System.currentTimeMillis()).toString() + ", " + recvdata.getsource() + ", " + recvdata.getdest() + ", " + recvseq + ", " + lastackedpkt + ", " + recvdata.getFIN());
				else
					lgfile.logfile(new Timestamp(System.currentTimeMillis()).toString() , recvdata.getsource() 
					, recvdata.getdest() , Integer.toString(recvseq) , Integer.toString(lastackedpkt) , recvdata.getFIN());
				
				if(recvseq == lastackedpkt + 1){
					recvdata.deliver(file);                     //deliver order packet
					lastackedpkt++;
					
					boolean goon;
					do{
						goon = false;
						Thread.sleep(10);
						for(int i = 0 ; i < recvbuf.length ; i++){
							if((recvbuf[i] != null) && (recvbuf[i].checkseqnumber() == lastackedpkt + 1)){
								recvbuf[i].deliver(file);       //deliver order packet in buffer
								lastackedpkt++;
								recvbuf[i] = null;
								goon = true;
								break;
							}
						}
					}while(goon);
					out.println("ACK" + lastackedpkt);
				}
				else if(recvseq <= lastackedpkt)                //discard duplicate packet
					out.println("ACK" + lastackedpkt);
				else if(recvseq > lastackedpkt + 1){
					Thread.sleep(10);
					for(int i = 0 ; i < recvbuf.length ; i++){
						if(recvbuf[i] == null){
                            byte[] bufdata = new byte[byteperpkt];
                            System.arraycopy(recvpkt, 0, bufdata, 0, recvpkt.length);
							recvbuf[i] = new TCPHeader2(bufdata);   //buffer the disorder packet
							break;
						}
					}
				}
				
			}
			out.println("ACKclose");                            //acknownledge FIN
			out.println("FIN");
			Thread.sleep(5000);
			System.out.println("close the receiver");
			receiver.close();
			sendack.close();
			System.exit(0);
		}
		catch(Exception e){
			System.out.println("error: " + e);
		}
		finally{
		}
		
	}
	
	public static boolean difffile(File f1 , File f2) throws IOException{
	
		@SuppressWarnings("resource")
		RandomAccessFile transfile= new RandomAccessFile(f1 , "r");
		@SuppressWarnings("resource")
		RandomAccessFile recvfile= new RandomAccessFile(f2 , "r");
		byte[] buffer1 = new byte[100];
		byte[] buffer2 = new byte[100];
		int count = (int) Math.ceil(f1.length() / (double)(100)) - 1;
		for(int i = 0 ; i < count ; i++){
			transfile.seek(100 * i);
			transfile.read(buffer1);
			String bufstr1 = new String(buffer1);
			recvfile.seek(100 * i);
			recvfile.read(buffer2);
			String bufstr2 = new String(buffer2);
			if(!bufstr1.equals(bufstr2))
				return false;
		}
		return true;
	}

}

class FileOperate2 {
	private File targetfile;
	
	public FileOperate2(File file){
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
	
	public void logfile(String timestamp , String source , String dest , String seqnumber , String ack , String FIN) throws IOException{
		String wbuf = timestamp + " " + source + " " + dest + " " + seqnumber + " " + ack + " " + FIN;
		@SuppressWarnings("resource")
		RandomAccessFile logfile = new RandomAccessFile(targetfile , "rw");
		logfile.seek(targetfile.length());
		logfile.writeBytes(wbuf);
		logfile.writeBytes("\r\n");
	}
	
	
}

class TCPHeader2 {
	private byte[] data;
	
	public TCPHeader2(byte[] d){
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


