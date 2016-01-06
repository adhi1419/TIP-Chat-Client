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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class SimpleServer {

	static String HELP_TEXT = "Use :quit for closing connection with server\n"
			+ "Use !msg <nick> <message> for sending PMs\n"
			+ "Check out //list, for list of Users connected to this server\n"
			+ "//startlog to start logging Messages(In ./log/<nick>.txt)\n"
			+ "//stoplog to stop logging messages";
	public static String SERV_HELP = "!kick <nick> for closing connection with that client\n"
			+ "!mute <nick> for mutingthat client\n"
			+ "!unmute <nick> for unmuting that nick\n"
			+ "//list for a list of your niggas!\n"
			+ ":quit to close the server";
	public static String SERVER_NICK = "<***Server***> ";

	public static HashMap<String, SimpleClientConnection> clients = new HashMap<String, SimpleClientConnection>();
	public static boolean isServerRunning = true;
	public static MessageQueue msgQueue = new MessageQueue();

	public static void main(String args[]) throws Exception {
		ServerSocket server = new ServerSocket(8888);
		new ServerOutput().start();
		int currentId = 0;
		System.out.println("Server listening for connections!");
		System.out.println("Type //servhelp for help!");
		msgQueue.start();
		while (isServerRunning) {
			Socket s = server.accept();
			System.out.println("New connection! ID: " + currentId);
			SimpleClientConnection scc = new SimpleClientConnection(currentId,
					s);
			scc.start();
			clients.put("Client--" + currentId, scc);
			currentId++;
		}
		server.close();
	}

	public static String getControlMessage(String cmessage) {
		if (cmessage.equals("help")) {
			return HELP_TEXT;
		} else if (cmessage.equals("list")) {
			String clientlist = "";
			for (String a : clients.keySet()) {
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
			if (clients.keySet().contains(a)) {
				return a + " is Online";
			} else {
				return a + " is Offline";
			}
		} else {
			return "Command not found";
		}
	}

	public static String getTimeStamp() {
		Date date = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("hh:mm:ss");
		return "[" + sdf.format(new Timestamp(date.getTime())) + "] ";
	}
}

class SimpleClientConnection extends Thread {
	int mId;
	private Socket mSocket;
	public boolean isNickMuted = false;
	String nick;

	public SimpleClientConnection(int id, Socket s) throws Exception {
		mId = id;
		mSocket = s;
	}

	@Override
	public void run() {
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(
					mSocket.getInputStream()));

			nick = (new Message(br.readLine())).getMessage();

			if (SimpleServer.clients.keySet().contains(nick)) {
				sendMessage(SimpleServer.getTimeStamp()
						+ SimpleServer.SERVER_NICK + "Nick " + nick
						+ " has already been taken");
				nick = "Client--" + mId;
			} else {
				SimpleServer.clients.put(nick,
						SimpleServer.clients.remove("Client--" + mId));
				System.out.println("*" + nick + " joined (ID: " + mId + ")");
				String line;
				while ((line = br.readLine()) != null) {
					if (!isNickMuted) {
						Message rec = new Message(line);
						rec.setNick(nick);
						if (rec.getMessageType().startsWith("PM")) {
							processPM(rec);
						} else if (rec.getMessageType().startsWith("BROADCAST")) {
							processBroadcast(rec);
						} else if (rec.getMessageType().startsWith(
								"CONTROLMESSAGE")) {
							processControlMessage(rec);
						}
					}
				}
			}
			System.out.println("*" + nick + " left (ID: " + mId + ")");
			quit();
			SimpleServer.clients.remove(nick);
		} catch (IOException e) {
			if (e.toString().contains("Socket closed")) {
				System.out.println(nick + "'s Socket was Closed!");
			} else {
				System.out.println("Error while processing Message in " + nick
						+ " :\n" + e);
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
			System.out.println("Error while sending Message in " + nick
					+ " :\n" + err);
		}
	}

	void processBroadcast(Message rec) {
		String txt = "[" + rec.getTime() + " | " + rec.getIP() + "] <"
				+ rec.getNick() + "> " + rec.getMessage();
		System.out.println(txt);
		for (String a : SimpleServer.clients.keySet()) {
			SimpleServer.msgQueue.addMessageToQueue(new Message(txt, a));
		}
	}

	void processPM(Message rec) {
		String txt = "[" + rec.getTime() + " | " + rec.getIP() + " | "
				+ rec.getMessageType().replace("--", " to ") + "] <"
				+ rec.getNick() + "> " + rec.getMessage();
		System.out.println(txt);
		String recepient = rec.getMessageType().substring(4);
		if (!SimpleServer.clients.keySet().contains(recepient)) {
			SimpleServer.msgQueue.addMessageToQueue(new Message(
					"Not Deliverd: " + txt, rec.getNick()));
		} else {
			SimpleServer.msgQueue.addMessageToQueue(new Message(txt, rec
					.getNick()));
			SimpleServer.msgQueue
					.addMessageToQueue(new Message(txt, recepient));
		}
	}

	void processControlMessage(Message rec) throws IOException {
		String txt = rec.getNick() + "~" + rec.getMessage();
		System.out.println(SimpleServer.getTimeStamp() + "<ServerView> "
				+ txt.substring(0, txt.indexOf("~"))
				+ " sent the following command: "
				+ txt.substring(txt.indexOf("~") + 1));
		txt = rec.getMessageType() + "--" + txt;
		String cmsg = txt.substring(txt.indexOf("~") + 3);
		SimpleServer.msgQueue.addMessageToQueue(new Message(SimpleServer
				.getTimeStamp()
				+ SimpleServer.SERVER_NICK
				+ "\n"
				+ SimpleServer.getControlMessage(cmsg), rec.getNick()));
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
}

class MessageQueue extends Thread {

	BlockingQueue<Message> msgQueue = new ArrayBlockingQueue<Message>(1024);

	@Override
	public void run() {
		while (SimpleServer.isServerRunning) {
			try {
				if (msgQueue.size() > 0) {
					Message msg = msgQueue.take();
					SimpleServer.clients.get(msg.getDestination()).sendMessage(
							msg.getMessage());
				}
			} catch (InterruptedException e) {
				System.out.println("Error while sending message from queue:\n"
						+ e);
			}
		}
	}

	public void addMessageToQueue(Message msg) {
		try {
			msgQueue.put(msg);
		} catch (InterruptedException e) {
			System.out.println("Error while adding message to queue:\n" + e);
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
				System.out.println(SimpleServer.getControlMessage("list"));
			} else if (line.equals("//servhelp")) {
				System.out.println(SimpleServer.SERV_HELP);
			} else {
				serverMessage(line);
			}
		}

		for (SimpleClientConnection a : SimpleServer.clients.values())
			a.quit();
		SimpleServer.isServerRunning = false;
		s.close();
	}

	void serverMessage(String line) {
		if (line.startsWith("!kick ")) {
			String asshole = line.substring(6);
			if (SimpleServer.clients.keySet().contains(asshole)) {
				SimpleServer.clients.get(asshole).sendMessage(
						SimpleServer.getTimeStamp() + SimpleServer.SERVER_NICK
								+ "You are being kicked!");
				SimpleServer.clients.get(asshole).quit();
				SimpleServer.clients.remove(asshole);
				System.out.println(SimpleServer.getTimeStamp()
						+ SimpleServer.SERVER_NICK + asshole + " was kicked!");
			} else {
				System.out.println(SimpleServer.getTimeStamp()
						+ SimpleServer.SERVER_NICK + asshole + " not found!");
			}
		} else if (line.startsWith("!mute")) {
			String asshole = line.substring(6);
			if (SimpleServer.clients.keySet().contains(asshole)) {
				SimpleServer.msgQueue.addMessageToQueue(new Message(
						SimpleServer.getTimeStamp() + SimpleServer.SERVER_NICK
								+ "You are muted, huehuehuehue!", asshole));
				SimpleServer.clients.get(asshole).isNickMuted = true;
				System.out.println(SimpleServer.getTimeStamp()
						+ SimpleServer.SERVER_NICK + asshole + " was muted!");
			} else {
				System.out.println(SimpleServer.getTimeStamp()
						+ SimpleServer.SERVER_NICK + asshole + " not found!");
			}
		} else if (line.startsWith("!unmute")) {
			String niceguy = line.substring(8);
			if (SimpleServer.clients.keySet().contains(niceguy)) {
				SimpleServer.clients.get(niceguy).isNickMuted = false;
				SimpleServer.msgQueue.addMessageToQueue(new Message(
						SimpleServer.getTimeStamp() + SimpleServer.SERVER_NICK
								+ "You can start wasting your life again!",
						niceguy));
				System.out.println(SimpleServer.getTimeStamp()
						+ SimpleServer.SERVER_NICK + niceguy + " was unmuted!");
			} else {
				System.out.println(SimpleServer.getTimeStamp()
						+ SimpleServer.SERVER_NICK + niceguy + " not found!");
			}
		} else {
			System.out.println(SimpleServer.getTimeStamp()
					+ SimpleServer.SERVER_NICK + line);
			for (String a : SimpleServer.clients.keySet()) {
				SimpleServer.msgQueue.addMessageToQueue(new Message(
						SimpleServer.getTimeStamp() + SimpleServer.SERVER_NICK
								+ line, a));
			}
		}
	}
}
