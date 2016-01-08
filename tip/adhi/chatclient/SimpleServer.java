package tip.adhi.chatclient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

class Vars {
	static String HELP_TEXT = "Use :quit for closing connection with server\n"
			+ "Use !msg <nick> <message> for sending PMs\n"
			+ "Check out //list, for list of Users connected to this server\n"
			+ "//checknick <nick> to check if that nick is Online\n"
			+ "//startlog to start logging Messages(In ./log/<nick>.txt)\n"
			+ "//stoplog to stop logging messages";
	static String SERV_HELP = "!kick <nick> for closing connection with that client\n"
			+ "!mute <nick> for mutingthat client\n"
			+ "!unmute <nick> for unmuting that nick\n"
			+ "!say <nick> <msg> to speak as the nick\n"
			+ "//list for a list of your niggas!\n"
			+ ":quit to close the server";
	static String SERVER_NICK = "<***Server***> ";
	public static String CLIENT_WELCOME_TEXT = "Use //help for Help on Commands you can use";

	static int PORT = 8888;
	static int mPingRate = 10;
	static int mPingTimeOut = 15;

	public static boolean ServerisRunning = true;

	public static HashMap<String, SimpleClientConnection> mClients = new HashMap<String, SimpleClientConnection>();

	public static MessageQueue mMsgQueue = new MessageQueue();

	static String getTimeStamp() {
		Date date = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("hh:mm:ss");
		return "[" + sdf.format(new Timestamp(date.getTime())) + "] ";
	}

	static String getControlMessage(String cmessage) {
		if (cmessage.equals("help")) {
			return Vars.HELP_TEXT;
		} else if (cmessage.equals("list")) {
			String clientlist = "";
			for (String a : Vars.mClients.keySet()) {
				clientlist += a + ", ";
			}
			clientlist = clientlist.substring(0, clientlist.length()
					- (clientlist.length() > 0 ? 2 : 0));
			return clientlist;
		} else if (cmessage.equals("startlog")) {
			return "You are messages are now being logged.";
		} else if (cmessage.equals("stoplog")) {
			return "You are messages are not being logged now.";
		} else if (cmessage.startsWith("checknick")) {
			String a = cmessage.substring(10);
			if (Vars.mClients.keySet().contains(a)) {
				return a + " is Online";
			} else {
				return a + " is Offline";
			}
		} else {
			return "Command not found";
		}
	}
}

public class SimpleServer {

	public static void main(String args[]) throws Exception {
		ServerSocket server = new ServerSocket(Vars.PORT);
		new ServerOutput().start();

		Vars.CLIENT_WELCOME_TEXT = "This server is running since "
				+ Vars.getTimeStamp() + "\n" + Vars.CLIENT_WELCOME_TEXT;

		int currentId = 0;

		System.out.println("Server listening for connections!");
		System.out.println("Type //servhelp for help!");

		Vars.mMsgQueue.start();

		while (Vars.ServerisRunning) {
			Socket s = server.accept();

			System.out.println("New connection! ID: " + currentId);

			SimpleClientConnection scc = new SimpleClientConnection(currentId,
					s);
			scc.start();

			Vars.mClients.put("Client--" + currentId, scc);
			currentId++;
		}
		server.close();
	}

}

class SimpleClientConnection extends Thread {
	int mId;
	private Socket mSocket;
	public boolean isNickMuted = false;
	String nick;

	Timer mTimer = new Timer();
	TimerTask mPing = mPing();
	TimerTask mPingTimeOut = mPingTimeOut();

	public SimpleClientConnection(int id, Socket s) throws Exception {
		mId = id;
		mSocket = s;
		nick = "Client--" + mId;
	}

	@Override
	public void run() {
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(
					mSocket.getInputStream()));

			nick = (new Message(br.readLine())).getMessage();

			if (Vars.mClients.keySet().contains(nick)) {
				sendMessage(Vars.getTimeStamp() + Vars.SERVER_NICK + "Nick "
						+ nick + " has already been taken");
				nick = "Client--" + mId;
			} else {
				Vars.mClients.put(nick, Vars.mClients.remove("Client--" + mId));
				System.out.println("*" + nick + " joined (ID: " + mId + ")");
				Vars.mMsgQueue.addMessageToQueue(new Message(
						Vars.CLIENT_WELCOME_TEXT, nick));
				String line;
				mTimer.schedule(mPing, Vars.mPingRate * 1000);
				while ((line = br.readLine()) != null) {
					Message rec = new Message(line);
					rec.setNick(nick);
					if (!isNickMuted) {
						if (rec.getMessageType().startsWith("PM")) {
							processPM(rec);
						} else if (rec.getMessageType().startsWith("BROADCAST")) {
							processBroadcast(rec);
						} else if (rec.getMessageType().startsWith(
								"CONTROLMESSAGE")) {
							processControlMessage(rec);
						}
					}
					if (rec.getMessageType().equals("PONG")) {
						mPingTimeOut.cancel();
						mPing = mPing();
						mTimer.schedule(mPing, Vars.mPingRate * 1000);
					}
				}
			}
			System.out.println("*" + nick + " left (ID: " + mId + ")");
			quit();
			Vars.mClients.remove(nick);
		} catch (IOException e) {
			if (e.toString().contains("Socket closed")) {
				System.out.println("*" + nick + "'s Socket was Closed!");
			} else {
				System.out.println("Error while receiving Message in " + nick
						+ ":\n" + e);
			}
		}
	}

	void sendMessage(String s) {
		try {
			OutputStreamWriter osw = new OutputStreamWriter(
					mSocket.getOutputStream());
			osw.write(s + "\n");
			osw.flush();
		} catch (IOException err) {
			if (!s.equals("PING")) {
				System.out.println("Error while sending Message in " + nick
						+ " :\n" + err);
			} else {
				System.out
						.println("Couldn't ping "
								+ nick
								+ "! Connection may have been forcefully closed by the Client!");
			}
		}
	}

	void processBroadcast(Message rec) {
		String txt = "[" + rec.getTime() + " | " + rec.getIP() + "] <"
				+ rec.getNick() + "> " + rec.getMessage();
		System.out.println(txt);
		for (String a : Vars.mClients.keySet()) {
			Vars.mMsgQueue.addMessageToQueue(new Message(txt, a));
		}
	}

	void processPM(Message rec) {
		String txt = "[" + rec.getTime() + " | " + rec.getIP() + " | "
				+ rec.getMessageType().replace("--", " to ") + "] <"
				+ rec.getNick() + "> " + rec.getMessage();
		System.out.println(txt);
		String recepient = rec.getMessageType().substring(4);
		if (!Vars.mClients.keySet().contains(recepient)) {
			Vars.mMsgQueue.addMessageToQueue(new Message(
					"Not Deliverd: " + txt, rec.getNick()));
		} else {
			Vars.mMsgQueue.addMessageToQueue(new Message(txt, rec.getNick()));
			Vars.mMsgQueue.addMessageToQueue(new Message(txt, recepient));
		}
	}

	void processControlMessage(Message rec) throws IOException {
		System.out.println(Vars.getTimeStamp() + "<ServerView> "
				+ rec.getNick() + " sent the following command: "
				+ rec.getMessage());
		String cmsg = rec.getMessage().substring(2);
		Vars.mMsgQueue.addMessageToQueue(new Message(Vars.getTimeStamp()
				+ Vars.SERVER_NICK + "\n" + Vars.getControlMessage(cmsg), rec
				.getNick()));
	}

	void quit() {
		// None of quit and kick messages are queued to avoid possible race over
		try {
			mSocket.close();
		} catch (IOException e) {
			System.out.println("Error while closing the connection with "
					+ nick + ":" + "\n" + e);
		}
	}

	void sleep(int seconds) {
		try {
			Thread.sleep(seconds * 1000);
		} catch (InterruptedException e) {
			System.out.println("Error while sleeping in thread " + nick + ":"
					+ "\n" + e);
		}
	}

	TimerTask mPingTimeOut() {
		return (new TimerTask() {
			@Override
			public void run() {
				System.out.println("*Lost Connection with " + nick + " (ID:"
						+ mId + ")");
				quit();
				Vars.mClients.remove(nick);
			}
		});
	}

	TimerTask mPing() {
		return (new TimerTask() {
			@Override
			public void run() {
				sendMessage("PING");
				mPingTimeOut = mPingTimeOut();
				mTimer.schedule(mPingTimeOut, Vars.mPingTimeOut * 1000);
			}
		});
	}
}

class MessageQueue extends Thread {

	BlockingQueue<Message> mMsgQueue = new ArrayBlockingQueue<Message>(1024);

	@Override
	public void run() {
		while (Vars.ServerisRunning) {
			try {
				if (mMsgQueue.size() > 0) {
					Message msg = mMsgQueue.take();
					Vars.mClients.get(msg.getDestination()).sendMessage(
							msg.getMessage());
				}
			} catch (InterruptedException e) {
				System.out
						.println("Error while sending a message from queue:\n"
								+ e);
			}
		}
	}

	public void addMessageToQueue(Message msg) {
		try {
			mMsgQueue.put(msg);
		} catch (InterruptedException e) {
			System.out.println("Error while adding a message to queue:\n" + e);
		}
	}
}

class ServerOutput extends Thread {
	@Override
	public void run() {
		String line;
		Scanner s = new Scanner(System.in);
		while (!((line = s.nextLine()).equals(":quit"))) {
			if (line.equals("//list")) {
				System.out.println(Vars.getControlMessage("list"));
			} else if (line.equals("//servhelp")) {
				System.out.println(Vars.SERV_HELP);
			} else {
				serverMessage(line);
			}
		}

		for (SimpleClientConnection a : Vars.mClients.values())
			a.quit();
		Vars.ServerisRunning = false;
		s.close();
		System.exit(0);
	}

	void serverMessage(String line) {
		if (line.startsWith("!kick ")) {
			String asshole = line.substring(6);
			if (Vars.mClients.keySet().contains(asshole)) {
				Vars.mClients.get(asshole).sendMessage(
						Vars.getTimeStamp() + Vars.SERVER_NICK
								+ "You are being kicked!");
				Vars.mClients.get(asshole).quit();
				Vars.mClients.remove(asshole);
				System.out.println(Vars.getTimeStamp() + Vars.SERVER_NICK
						+ asshole + " was kicked!");
			} else {
				System.out.println(Vars.getTimeStamp() + Vars.SERVER_NICK
						+ asshole + " not found!");
			}
		} else if (line.startsWith("!mute ")) {
			String asshole = line.substring(6);
			if (Vars.mClients.keySet().contains(asshole)) {
				Vars.mMsgQueue.addMessageToQueue(new Message(Vars
						.getTimeStamp()
						+ Vars.SERVER_NICK
						+ "You are muted, huehuehuehue!", asshole));
				Vars.mClients.get(asshole).isNickMuted = true;
				System.out.println(Vars.getTimeStamp() + Vars.SERVER_NICK
						+ asshole + " was muted!");
			} else {
				System.out.println(Vars.getTimeStamp() + Vars.SERVER_NICK
						+ asshole + " not found!");
			}
		} else if (line.startsWith("!unmute ")) {
			String niceguy = line.substring(8);
			if (Vars.mClients.keySet().contains(niceguy)) {
				Vars.mClients.get(niceguy).isNickMuted = false;
				Vars.mMsgQueue.addMessageToQueue(new Message(Vars
						.getTimeStamp()
						+ Vars.SERVER_NICK
						+ "You can start wasting your life again!", niceguy));
				System.out.println(Vars.getTimeStamp() + Vars.SERVER_NICK
						+ niceguy + " was unmuted!");
			} else {
				System.out.println(Vars.getTimeStamp() + Vars.SERVER_NICK
						+ niceguy + " not found!");
			}
		} else if (line.startsWith("!say ")) {
			String msg = line.substring(5);
			String nick = msg.substring(0, line.indexOf(" "));
			msg = msg.substring(msg.indexOf(" ") + 1);
			System.out.println(Vars.getTimeStamp() + "Spoke as " + nick + ": "
					+ msg);
			broadcast(Vars.getTimeStamp() + "<" + nick + "> " + msg);
		} else {
			System.out.println(Vars.getTimeStamp() + Vars.SERVER_NICK + line);
			broadcast(Vars.getTimeStamp() + Vars.SERVER_NICK + line);
		}
	}

	void broadcast(String s) {
		for (String a : Vars.mClients.keySet()) {
			Vars.mMsgQueue.addMessageToQueue(new Message(s, a));
		}
	}
}
