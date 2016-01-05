import java.io.ObjectOutputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.File;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Scanner;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.sql.Timestamp;
import java.net.UnknownHostException;
import java.net.Socket;

public class SimpleClient {
	static String msg;
	static String timeStamp;
	static String ip;
	public static String nick;
	public static boolean log = false;
	static Socket sock;
	public static PrintWriter logger;

	public static void main(String[] args) throws Exception {

		sock = new Socket((args.length == 1 ? args[0] : "localhost"), 8888);

		Scanner s = new Scanner(System.in);

		/*
		 * Message Object, which is sent over the Socket, once the Client sets a
		 * nick. The Message class contains a Message Parser and also carries a
		 * Client's Nick and also status(Online/Offline).
		 */
		Message msg = new Message();

		ObjectOutputStream oos = new ObjectOutputStream(sock.getOutputStream());

		// Setting nick
		while (nick == null) {
			System.out.print("Enter your Nick: ");
			nick = s.nextLine();
			if (nick.contains("*") || nick.contains("~") || nick.contains("--")) {
				System.out
						.println("Nick contains Invalid Character(s) (~/--/*)");
				nick = null;
			}
		}
		msg.setNick(nick);
		oos.writeObject(msg); // The Message Object is now written to the stream

		// This Thread listens for messages from the Server
		ClientReceiver cr = new ClientReceiver(sock);
		cr.start();

		System.out.println("Use //help for Help");

		// The Client chats here
		String line;
		while (!((line = s.nextLine()).equals(":quit"))) {
			if (line.equals("//startlog"))
				log = true;
			else if (line.equals("//stoplog")) {
				log = false;
				SimpleClient
						.logMessage("You are messages are not being logged now.");
			}
			if (!(line.contains("~") || line.contains("--"))) {
				if(line.length() > 0)
					sendMessage(line);
			} else {
				System.out
						.println("Message contains Invalid Character(s) (~/--)");
			}
		}
		oos.close();
		s.close();
	}

	/*
	 * This method takes the text the client types, encapsulates the Message
	 * Type, Time of message, Sender's Nick and the Message itself and sends it
	 * to the server.
	 */
	static void sendMessage(String message) {
		String msgtype = "";
		msg = message;

		Date date = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("hh:mm:ss");
		timeStamp = sdf.format(new Timestamp(date.getTime()));

		try {
			Socket s = new Socket("192.168.1.1", 80);
			ip = s.getLocalAddress().getHostAddress();
			s.close();

			OutputStreamWriter osw = new OutputStreamWriter(
					sock.getOutputStream());
			if (msg.startsWith("!msg ")) {
				msg = msg.substring(5);
				String recepient = msg.substring(0, msg.indexOf(" "));
				msg = msg.substring(msg.indexOf(" ") + 1);
				msgtype = "PM--" + recepient;
			} else if (msg.startsWith("//")) {
				msgtype = "CONTROLMESSAGE";
			} else {
				msgtype = "BROADCAST";
			}
			osw.write(msgtype + "~" + timeStamp + "~" + ip + "~" + msg + "\n");
			osw.flush();
		} catch (UnknownHostException u) {

		} catch (IOException io) {

		}
	}

	static void logMessage(String message) {
		try {
			if (!new File("./log").exists()) {
				new File("./log").mkdir();
			}
			logger = new PrintWriter(new FileWriter("./log/" + nick + ".txt",
					true));
			logger.println(message);
			logger.flush();
			logger.close();
		} catch (IOException io) {

		}
	}
}

class ClientReceiver extends Thread {
	Socket mSocket;

	public ClientReceiver(Socket s) throws Exception {
		mSocket = s;
	}

	@Override
	public void run() {
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(
					mSocket.getInputStream()));
			String line;
			while ((line = br.readLine()) != null) {
				System.out.println(line);
				if (SimpleClient.log) {
					SimpleClient.logMessage(line);
				}
			}
		} catch (IOException e) {
			// e.printStackTrace(System.out); //Too much spam for layman users
		}

	}
}
