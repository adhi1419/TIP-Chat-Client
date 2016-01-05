/* I have not included the package name, because I
*  was having issues importing the Message class
*  if I give this a package name(I used the same 
*  package name in the Message class too)
*  PS: I'm not using any IDE, so, I cannot 
*  create a project which the IDE automatically manages
*  under the same package.
*/

import java.util.Date;
import java.text.SimpleDateFormat;
import java.sql.Timestamp;
import java.net.Socket;
import java.net.UnknownHostException;
import java.io.IOException;
import java.io.Serializable;
import java.util.Scanner;

public class Message implements Serializable {
	String msg;
	String msgtype;
	String ip;
	String time;
	String nick;
	boolean status = true;

	void parseMessage(String message){
		Scanner splitter = new Scanner(message);
		splitter.useDelimiter("~");
		msgtype = splitter.next();
		time = splitter.next();
		ip = splitter.next();
		msg = splitter.next();
		splitter.close();
	}

	void setNick(String nickname){
		nick = nickname;
	}

	void leave(){
		status = false;
	}

	String getNick(){
		return nick;
	}

	String getMessageType(){
		return msgtype;
	}

	String getTime(){
		return time;
	}

	String getIP(){
		return ip;
	}

	String getMessage(){
		return msg;
	}

	boolean isOnline(){
		return status;
	}
}