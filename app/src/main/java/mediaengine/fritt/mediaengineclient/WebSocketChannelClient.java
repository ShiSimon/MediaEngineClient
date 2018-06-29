package mediaengine.fritt.mediaengineclient;

import android.os.Handler;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;

import de.tavendo.autobahn.WebSocket;
import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketException;
import de.tavendo.autobahn.WebSocketOptions;

public class WebSocketChannelClient {
    private static final String TAG = "WSChannelRTCClient";
    private static final int CLOSE_TIMEOUT = 1000;
    private final WebSocketChannelEvents events;
    private final Handler handler;
    private WebSocketConnection ws;
    private WebSocketOptions wsOption;
    private WebSocketObserver wsObserver;
    private String wsServerUrl;
    private String postServerUrl;
    private String roomID;
    private String clientID;
    private WebSocketConnectionState state;
    private final Object closeEventLock = new Object();
    private boolean closeEvent;

    private String mms;
    private String tran;
    // WebSocket send queue. Messages are added to the queue when WebSocket
    // client is not registered and are consumed in register() call.
    private final LinkedList<String> wsSendQueue;

    /**
     * Possible WebSocket connection states.
     */
    public enum WebSocketConnectionState { NEW, CONNECTED, REGISTERED, CLOSED, ERROR }

    /**
     * Callback interface for messages delivered on WebSocket.
     * All events are dispatched from a looper executor thread.
     */
    public interface WebSocketChannelEvents {
        void onWebSocketMessage(final String message);
        void onWebSocketClose();
        void onWebSocketError(final String description);
    }

    public WebSocketChannelClient(Handler handler, WebSocketChannelEvents events) {
        this.handler = handler;
        this.events = events;
        roomID = null;
        clientID = null;
        wsSendQueue = new LinkedList<String>();
        state = WebSocketConnectionState.NEW;
    }

    public WebSocketConnectionState getState() {
        return state;
    }

    public void connect(final String wsUrl, final String postUrl) {
        checkIfCalledOnValidThread();
        if (state != WebSocketConnectionState.NEW) {
            Log.e(TAG, "WebSocket is already connected.");
            return;
        }
        wsServerUrl = wsUrl;
        this.tran = postUrl;
        closeEvent = false;

        Log.d(TAG, "Connecting WebSocket to: " + wsUrl + ". Post URL: " + postUrl);
        wsOption = new WebSocketOptions();
        wsOption.setSocketConnectTimeout(5000);
        String[] sub = new String[1];
        sub[0] = "mms";
        ws = new WebSocketConnection();

        wsObserver = new WebSocketObserver();
        try {
            ws.connect(new URI(wsServerUrl), sub,wsObserver,wsOption);
        } catch (URISyntaxException e) {
            reportError("URI error: " + e.getMessage());
        } catch (WebSocketException e) {
            reportError("WebSocket connection error: " + e.getMessage());
        }
    }

    public void register(String mms,String transaction) {
        Log.d(TAG,"in register");
        checkIfCalledOnValidThread();
        if (state != WebSocketConnectionState.CONNECTED) {
            Log.w(TAG, "WebSocket register() in state " + state);
            return;
        }
        JSONObject json = new JSONObject();
        try {
            json.put("mms", "create");
            json.put("transaction", transaction);
            Log.d(TAG, "C->S: " + json.toString());
            ws.sendTextMessage(json.toString());
            state = WebSocketConnectionState.REGISTERED;
            // Send any previously accumulated messages.
           /* for (String sendMessage : wsSendQueue) {
                send(sendMessage);
            }
            wsSendQueue.clear();*/
        } catch (JSONException e) {
            reportError("WebSocket register JSON error: " + e.getMessage());
        }
    }

    public void sendAttach(ServerConnectionManager.MessageHandler handler,long session_id,String service_name,
                           String opaque_id){
        JSONObject json = new JSONObject();
        try{
            json.put("mms",handler.getMms());
            json.put("transaction",handler.getTransaction());
            json.put("session_id",session_id);
            json.put("service",service_name);
            json.put("opaque_id",opaque_id);
            String message = json.toString();
            Log.d(TAG,"C->S :" + message);
            ws.sendTextMessage(message);
        }catch (JSONException e){
            reportError("sendAttach error: " + e.getMessage());
        }
    }

    public void sendLocalMessage(ServerConnectionManager.MessageHandler handler,
                                 long session_id,long handle_id,boolean videoenable,boolean audioenable)
    {
        JSONObject json = new JSONObject();
        try{
            json.put("mms",handler.getMms());
            json.put("transaction",handler.getTransaction());
            json.put("session_id",session_id);
            json.put("handle_id",handle_id);
            JSONObject data = new JSONObject();
            data.put("audio",true);
            data.put("video",true);
            String data_string = data.toString();
            json.put("body",data);
            String message = json.toString();
            Log.d(TAG,"C->S :" + message);
            ws.sendTextMessage(message);
        }catch (JSONException e){
            reportError("sendAttach error: " + e.getMessage());
        }
    }

    public void send(String message) {
        checkIfCalledOnValidThread();
        switch (state) {
            case NEW:
            case CONNECTED:
                // Store outgoing messages and send them after websocket client
                // is registered.
                Log.d(TAG, "WS ACC: " + message);
                wsSendQueue.add(message);
                return;
            case ERROR:
            case CLOSED:
                Log.e(TAG, "WebSocket send() in error or closed state : " + message);
                return;
            case REGISTERED:
                /*JSONObject json = new JSONObject();
                try {
                    json.put("cmd", "send");
                    json.put("msg", message);
                    message = json.toString();
                    Log.d(TAG, "C->WSS: " + message);
                    ws.sendTextMessage(message);
                } catch (JSONException e) {
                    reportError("WebSocket send JSON error: " + e.getMessage());
                }*/
                ws.sendTextMessage(message);
                Log.d(TAG, "C->S: " + message);
                break;
        }
    }

    public void disconnect(boolean waitForComplete,String tran,long session_id) {
        checkIfCalledOnValidThread();
        Log.d(TAG, "Disconnect WebSocket. State: " + state);

        JSONObject json = new JSONObject();
        try{
            json.put("mms","destroy");
            json.put("transaction",tran);
            json.put("session_id",session_id);
            Log.d(TAG,"C->S: " + json.toString());
            ws.sendTextMessage(json.toString());
        }catch (JSONException e){
            reportError("error in disconnect" + e.getMessage());
        }

        ws.disconnect();
        Log.d(TAG, "Disconnecting WebSocket done.");

        // Wait for websocket close event to prevent websocket library from
        // sending any pending messages to deleted looper thread.
        if (waitForComplete) {
            synchronized (closeEventLock) {
                while (!closeEvent) {
                    try {
                        closeEventLock.wait(CLOSE_TIMEOUT);
                        break;
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Wait error: " + e.toString());
                    }
                }
            }
        }
    }

    private void reportError(final String errorMessage) {
        Log.e(TAG, errorMessage);
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (state != WebSocketConnectionState.ERROR) {
                    state = WebSocketConnectionState.ERROR;
                    events.onWebSocketError(errorMessage);
                }
            }
        });
    }

    // Helper method for debugging purposes. Ensures that WebSocket method is
    // called on a looper thread.
    private void checkIfCalledOnValidThread() {
        if (Thread.currentThread() != handler.getLooper().getThread()) {
            throw new IllegalStateException("WebSocket method is not called on valid thread");
        }
    }

    private class WebSocketObserver implements WebSocket.WebSocketConnectionObserver {
        @Override
        public void onOpen() {
            Log.d(TAG, "WebSocket connection opened to: " + wsServerUrl);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    state = WebSocketConnectionState.CONNECTED;
                    // Check if we have pending register request.
                    /*if (roomID != null && clientID != null) {
                        register(roomID, clientID);
                    }*/
                    register(mms,tran);
                }
            });
        }

        @Override
        public void onClose(WebSocketCloseNotification code, String reason) {
            Log.d(TAG, "WebSocket connection closed. Code: " + code + ". Reason: " + reason + ". State: "
                    + state);
            synchronized (closeEventLock) {
                closeEvent = true;
                closeEventLock.notify();
            }
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (state != WebSocketConnectionState.CLOSED) {
                        state = WebSocketConnectionState.CLOSED;
                        events.onWebSocketClose();
                    }
                }
            });
        }

        @Override
        public void onTextMessage(String payload) {
            Log.d(TAG, "S->C: " + payload);
            final String message = payload;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (state == WebSocketConnectionState.CONNECTED
                            || state == WebSocketConnectionState.REGISTERED) {
                        Log.d(TAG,"go to onWebSocketMessage");
                        events.onWebSocketMessage(message);
                    }
                }
            });
        }

        @Override
        public void onRawTextMessage(byte[] payload) {}

        @Override
        public void onBinaryMessage(byte[] payload) {}
    }
}