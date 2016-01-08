package tip.adhi.chatclient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;

public class SimpleClient {
	static String msg;
	static String timeStamp;
	static String ip;
	public static String nick;
	public static boolean log = false;
	static Socket sock;
	public static PrintWriter logger;
	static int PORT = 8888;

	public static void main(String[] args) throws Exception {

		sock = new Socket((args.length == 1 ? args[0] : "localhost"), PORT);

		Scanner s = new Scanner(System.in);

		// Setting nick
		while (nick == null) {
			System.out.print("Enter your Nick: ");
			nick = s.nextLine();
			if (nick.contains("~") || nick.contains("--") || nick.contains(" ")) {
				System.out
						.println("Nick contains Invalid Character(s) (~/--) or a space");
				nick = null;
			}
		}

		sendMessage(nick);

		// This Thread listens for messages from the Server
		ClientReceiver cr = new ClientReceiver(sock);
		cr.start();

		// The Client chats here
		String line;
		while (!((line = s.nextLine()).equals(":quit"))) {

			if (line.equals("//startlog"))
				log = true;
			else if (line.equals("//stoplog")) {
				log = false;
				logMessage("Your messages are not being logged now.");
			}

			if (!(line.contains("~") || line.contains("--"))) {
				if (line.length() > 0)
					sendMessage(line);
			} else {
				System.out
						.println("Message contains Invalid Character(s) (~/--)");
			}
		}
		s.close();
		sock.close();
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
			System.out.println("Error while sending Message:\n" + u);
		} catch (IOException io) {
			System.out.println("Error while sending Message:\n" + io);
		}
	}

	static void sendPong() {
		try {
			OutputStreamWriter osw = new OutputStreamWriter(
					sock.getOutputStream());
			osw.write("PONG~ ~ ~ \n");
			osw.flush();
		} catch (IOException e) {
			System.out.println("Error while sending Pong:\n" + e);
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
			System.out.println("Error while logging Message:\n" + io);
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
				if (!line.equals("PING")) {
					System.out.println(line);
					if (SimpleClient.log) {
						SimpleClient.logMessage(line);
					}
				} else {
					SimpleClient.sendPong();
				}
			}

			mSocket.close();
			System.exit(0);

		} catch (IOException e) {

			if (e.toString().contains("Socket closed")) {
				System.out
						.println("The Server closed the connection with the Client!");
			} else {
				if (e.toString().contains("Connection reset")) {
					System.out.println("The Server was closed!");
					System.exit(0);
				} else {
					System.out.println("Error while receiving Message:\n" + e);
				}
			}

		}
	}
}