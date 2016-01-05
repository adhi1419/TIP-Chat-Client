import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class SimpleServer {

	static String helptext = "Use :quit for closing connection with server\n"
			+ "Use !msg <nick> <message> for sending PMs\n"
			+ "Check out //list, for list of Users connected to this server\n"
			+ "//startlog to start logging Messages(In ./log/<nick>.txt)\n"
			+ "//stoplog to stop logging messages";
	public static String servhelp = "!kick <nick> for closing connection with that client\n"
			+ "!mute <nick> for mutingthat client\n"
			+ "!unmute <nick> for unmuting that nick\n"
			+ "//list for a list of your niggas!\n"
			+ ":quit to close the server";

	public static ArrayList<String> clients = new ArrayList<String>();
	public static ArrayList<SimpleClientConnection> clientconnections = new ArrayList<SimpleClientConnection>();
	public static boolean isServerRunning = true;
	public static MessageQueue msgQueue = new MessageQueue();

	public static void main(String args[]) throws Exception {
		ServerSocket server = new ServerSocket(8888);
		new ServerOutput().start();
		long currentId = 0;
		System.out.println("Server listening for connections!");
		System.out.println("Type //servhelp for help!");
		msgQueue.start();
		while (isServerRunning) {
			Socket s = server.accept();
			System.out.println("New connection! ID: " + currentId);
			SimpleClientConnection scc = new SimpleClientConnection(currentId,
					s);
			scc.start();
			clientconnections.add((int) currentId, scc);
			clients.add((int) currentId++, "placeholder");
		}
		server.close();
	}

	public static String getControlMessage(String cmessage) {
		if (cmessage.equals("help")) {
			return helptext;
		} else if (cmessage.equals("list")) {
			// If nick ends with *, the client is Offline
			// If nick ends with **, the client is Kicked
			// If nick ends with ***, the client is Muted
			String clientlist = "";
			for (String a : clients) {
				if (!a.contains("**")) {
					clientlist += a.replace("*", " (Offline)") + ", ";
				} else {
					if (!a.contains("***"))
						clientlist += a.replace("**", " (Kicked)") + ", ";
				}
			}
			clientlist = clientlist.substring(0, clientlist.length() - 2);
			return clientlist;
		} else if (cmessage.equals("startlog")) {
			return "You are messages are now being logged.";
		} else if (cmessage.equals("stoplog")) {
			return "You are messages are not being logged now.";
		} else if (cmessage.startsWith("checknick")) {
			String a = cmessage.substring(10);
			if (clients.contains(a)) {
				return "The nick " + a + " is Taken";
			} else {
				return "The nick " + a + " is Available";
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
	long mId;
	private Socket mSocket;
	String nick;

	public SimpleClientConnection(long id, Socket s) throws Exception {
		mId = id;
		mSocket = s;
	}

	@Override
	public void run() {
		try {
			ObjectInputStream in = new ObjectInputStream(
					mSocket.getInputStream());
			Message msg = (Message) in.readObject();
			nick = msg.getNick();
			SimpleServer.clients.set((int) mId, nick);
			System.out.println("*" + nick + " joined (ID: " + mId + ")");

			try {
				BufferedReader br = new BufferedReader(new InputStreamReader(
						mSocket.getInputStream()));
				String line;
				while ((line = br.readLine()) != null) {
					if (!SimpleServer.clients.get((int) mId).contains("***")) {
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
				mSocket.close();
				System.out.println("*" + nick + " left (ID: " + mId + ")");
				SimpleServer.clients.set((int) mId, nick + "*");
			} catch (IOException e) {

			}
		} catch (IOException e) {

		} catch (ClassNotFoundException c) {

		}
	}

	void sendMessage(String s) {
		try {
			OutputStreamWriter osw = new OutputStreamWriter(
					mSocket.getOutputStream());
			osw.write(s + "\n");
			osw.flush();
		} catch (IOException err) {
		}
	}

	void processBroadcast(Message rec) {
		String txt = "[" + rec.getTime() + " | " + rec.getIP() + "] <"
				+ rec.getNick() + "> " + rec.getMessage();
		System.out.println(txt);
		for (String a : SimpleServer.clients) {
			SimpleServer.msgQueue.addMessageToQueue(new Message(txt,
					SimpleServer.clients.indexOf(a)));
		}
	}

	void processPM(Message rec) {
		String txt = "[" + rec.getTime() + " | " + rec.getIP() + " | "
				+ rec.getMessageType().replace("--", " to ") + "] <"
				+ rec.getNick() + "> " + rec.getMessage();
		System.out.println(txt);
		String recepient = rec.getMessageType().substring(4);
		if (!SimpleServer.clients.contains(recepient)) {
			SimpleServer.msgQueue.addMessageToQueue(new Message(
					"Not Deliverd: " + txt, SimpleServer.clients.indexOf(rec
							.getNick())));
		} else {
			SimpleServer.msgQueue.addMessageToQueue(new Message(txt,
					SimpleServer.clients.indexOf(rec.getNick())));
			SimpleServer.msgQueue.addMessageToQueue(new Message(txt,
					SimpleServer.clients.indexOf(recepient)));
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
				+ "<***Server***>\n"
				+ SimpleServer.getControlMessage(cmsg) + "\n",
				SimpleServer.clients.indexOf(rec.getNick())));
	}

	void quit() {
		sendMessage(SimpleServer.getTimeStamp() + "<***Server***> "
				+ "Connection with Client " + mId + " is being closed.");
		try {
			mSocket.close();
		} catch (IOException e) {

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
					SimpleServer.clientconnections.get(msg.getDestinationID())
							.sendMessage(msg.getMessage());
				}
			} catch (InterruptedException e) {
			}
		}
	}

	public void addMessageToQueue(Message msg) {
		try {
			msgQueue.put(msg);
		} catch (InterruptedException e) {
			System.out.println("Error: " + e);
		}
	}
}

class ServerOutput extends Thread {
	@Override
	public void run() {
		String line;
		Scanner s = new Scanner(System.in);
		while (!((line = s.nextLine()).equals(":quit"))) {
			// Will find a better home for these some time later
			if (line.equals("//list")) {
				System.out.println(SimpleServer.getControlMessage("list"));
			} else if (line.equals("//servhelp")) {
				System.out.println(SimpleServer.servhelp);
			} else {
				broadcast(line);
			}
		}

		for (SimpleClientConnection a : SimpleServer.clientconnections)
			a.quit();

		SimpleServer.isServerRunning = false;
		s.close();
	}

	void broadcast(String line) {
		if (line.startsWith("!kick ")) {
			String asshole = line.substring(6);
			if (SimpleServer.clients.contains(asshole)) {
				int id = SimpleServer.clients.indexOf(asshole);
				SimpleServer.msgQueue.addMessageToQueue(new Message(
						SimpleServer.getTimeStamp() + "<***Server***> "
								+ "You are being Kicked!", id));
				SimpleServer.clientconnections.get(id).quit();
				SimpleServer.clients.set(id, asshole + "**");
				System.out.println(SimpleServer.getTimeStamp()
						+ "<***Server***> " + asshole + " was kicked!");
			} else {
				System.out.println(SimpleServer.getTimeStamp()
						+ "<***Server***> " + asshole + " not found!");
			}
		} else if (line.startsWith("!mute")) {
			String asshole = line.substring(6);
			if (SimpleServer.clients.contains(asshole)) {
				int id = SimpleServer.clients.indexOf(asshole);
				SimpleServer.msgQueue.addMessageToQueue(new Message(
						SimpleServer.getTimeStamp() + "<***Server***> "
								+ "You are muted, huehuehuehue!", id));
				SimpleServer.clients.set(id, asshole + "***");
				System.out.println(SimpleServer.getTimeStamp()
						+ "<***Server***> " + asshole + " was muted!");
			} else {
				System.out.println(SimpleServer.getTimeStamp()
						+ "<***Server***> " + asshole + " not found!");
			}
		} else if (line.startsWith("!unmute")) {
			String niceguy = line.substring(8);
			if (SimpleServer.clients.contains(niceguy + "***")) {
				int id = SimpleServer.clients.indexOf(niceguy + "***");
				SimpleServer.clients.set(id, niceguy);
				SimpleServer.msgQueue
						.addMessageToQueue(new Message(SimpleServer
								.getTimeStamp()
								+ "<***Server***> "
								+ "You can start wasting your life again!", id));
				System.out.println(SimpleServer.getTimeStamp()
						+ "<***Server***> " + niceguy + " was unmuted!");
			} else {
				System.out.println(SimpleServer.getTimeStamp()
						+ "<***Server***> " + niceguy + " not found!");
			}
		} else {
			System.out.println(SimpleServer.getTimeStamp() + "<***Server***> "
					+ line);
			for (SimpleClientConnection a : SimpleServer.clientconnections) {
				SimpleServer.msgQueue.addMessageToQueue(new Message(
						SimpleServer.getTimeStamp() + "<***Server***> " + line,
						(int) a.mId));
			}
		}
	}
}
