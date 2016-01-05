import java.io.Serializable;
import java.util.Scanner;

public class Message implements Serializable {

	private static final long serialVersionUID = 1L;
	String msg;
	String msgtype;
	String ip;
	String time;
	String nick;
	boolean status = true;

	void parseMessage(String message) {
		Scanner splitter = new Scanner(message);
		splitter.useDelimiter("~");
		msgtype = splitter.next();
		time = splitter.next();
		ip = splitter.next();
		msg = splitter.next();
		splitter.close();
	}

	void setNick(String nickname) {
		nick = nickname;
	}

	void leave() {
		status = false;
	}

	String getNick() {
		return nick;
	}

	String getMessageType() {
		return msgtype;
	}

	String getTime() {
		return time;
	}

	String getIP() {
		return ip;
	}

	String getMessage() {
		return msg;
	}

	boolean isOnline() {
		return status;
	}
}