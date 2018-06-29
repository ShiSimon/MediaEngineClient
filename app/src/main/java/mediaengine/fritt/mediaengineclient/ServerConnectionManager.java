package mediaengine.fritt.mediaengineclient;

import java.util.LinkedList;
import java.util.Random;

public class ServerConnectionManager {
    public LinkedList<MessageHandler> messageHandler;
    public long session_id;
    public long handle_id;
    public String service_name;
    public String opaque_id;

    public ServerConnectionManager(){
        messageHandler = new LinkedList<MessageHandler>();
    }

    public static class MessageHandler{
        private final String mms;
        private final String transaction;

        public MessageHandler(String mms){
            this.mms = mms;
            this.transaction = getRandomString(12);
        }

        public String getTransaction() {
            return transaction;
        }

        public String getMms() {
            return mms;
        }
    }


    public static String getRandomString(int length){
        String str="abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random=new Random();
        StringBuffer sb=new StringBuffer();
        for(int i=0;i<length;i++){
            int number=random.nextInt(62);
            sb.append(str.charAt(number));
        }
        return sb.toString();
    }
}
