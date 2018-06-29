package mediaengine.fritt.mediaengineclient;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedList;

import mediaengine.fritt.mediaengine.ClientInterface;
import mediaengine.fritt.mediaengine.IceCandidateInfo;
import mediaengine.fritt.mediaengine.IceServerInfo;
import mediaengine.fritt.mediaengine.SessionDescriptionInfo;

/**
 * Negotiates signaling for chatting with https://appr.tc "rooms".
 * Uses the client<->server specifics of the apprtc AppEngine webapp.
 *
 * <p>To use: create an instance of this object (registering a message handler) and
 * call connectToRoom().  Once room connection is established
 * onConnectedToRoom() callback with room parameters is invoked.
 * Messages to other party (with local Ice candidates and answer SDP) can
 * be sent after WebSocket connection is established.
 */
public class WebSocketClient implements ClientInterface, WebSocketChannelClient.WebSocketChannelEvents {
    private static final String TAG = "WSClient";

    private enum ConnectionState { NEW, CONNECTED, CLOSED, ERROR }

    private final Handler handler;
    private boolean initiator;
    private SignalingEvents events;
    private WebSocketChannelClient wsClient;
    private ConnectionState roomState;
    private RoomConnectionParameters connectionParameters;
    private String messageUrl;
    private String leaveUrl;
    private ServerConnectionManager serverConnectionManager;
    private SignalingParameters params;

    public WebSocketClient(SignalingEvents events) {
        this.events = events;
        roomState = ConnectionState.NEW;
        serverConnectionManager = new ServerConnectionManager();
        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    // --------------------------------------------------------------------
    // AppRTCClient interface implementation.
    // Asynchronously connect to an AppRTC room URL using supplied connection
    // parameters, retrieves room parameters and connect to WebSocket server.
    @Override
    public void connectToRoom(RoomConnectionParameters connectionParameters) {
        this.connectionParameters = connectionParameters;
        params = new SignalingParameters(new LinkedList<IceServerInfo>(), true,
                null, "ws://10.0.1.116:6660",null,null,null);
        handler.post(new Runnable() {
            @Override
            public void run() {
                connectToRoomInternal();
            }
        });
    }

    @Override
    public void disconnectFromRoom() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                disconnectFromRoomInternal();
                handler.getLooper().quit();
            }
        });
    }

    // Connects to room - function runs on a local looper thread.
    private void connectToRoomInternal() {
        roomState = ConnectionState.NEW;
        wsClient = new WebSocketChannelClient(handler, this);
        Log.d(TAG,"connect");

        ServerConnectionManager.MessageHandler create_message = new ServerConnectionManager.MessageHandler("create");
        serverConnectionManager.messageHandler.add(create_message);
        wsClient.connect("ws://10.0.1.116:6660", create_message.getTransaction());
    }

    // Disconnect from room and send bye messages - runs on a local looper thread.
    private void disconnectFromRoomInternal() {
        Log.d(TAG, "Disconnect. Room state: " + roomState);
        roomState = ConnectionState.CLOSED;
        if (wsClient != null) {
            ServerConnectionManager.MessageHandler destroy = new ServerConnectionManager.MessageHandler("destroy");
            wsClient.disconnect(true,destroy.getTransaction(),serverConnectionManager.session_id);
        }
    }


    // Send local offer SDP to the other participant.
    @Override
    public void sendOfferSdp(final SessionDescriptionInfo sdp) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.CONNECTED) {
                    reportError("Sending offer SDP in non connected state.");
                    return;
                }
                JSONObject upjson = new JSONObject();
                ServerConnectionManager.MessageHandler offer = new ServerConnectionManager.MessageHandler("message");
                serverConnectionManager.messageHandler.add(offer);
                jsonPut(upjson,"mms",offer.getMms());
                jsonPut(upjson,"transaction",offer.getTransaction());
                jsonPut(upjson,"session_id",serverConnectionManager.session_id);
                jsonPut(upjson,"handle_id",serverConnectionManager.handle_id);
                JSONObject body = new JSONObject();
                jsonPut(body,"audio",true);
                jsonPut(body,"video",true);
                jsonPut(upjson,"body",body);
                JSONObject json = new JSONObject();
                jsonPut(json, "sdp", sdp.description);
                jsonPut(json, "type", "offer");
                jsonPut(upjson,"jsep",json);
                Log.d(TAG,"C->S :" + upjson.toString());
                wsClient.send(upjson.toString());
            }
        });
    }

    // Send local answer SDP to the other participant.
    @Override
    public void sendAnswerSdp(final SessionDescriptionInfo sdp) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (connectionParameters.loopback) {
                    Log.e(TAG, "Sending answer in loopback mode.");
                    return;
                }
                JSONObject json = new JSONObject();
                jsonPut(json, "sdp", sdp.description);
                jsonPut(json, "type", "answer");
                wsClient.send(json.toString());
            }
        });
    }

    // Send Ice candidate to the other participant.
    @Override
    public void sendLocalIceCandidate(final IceCandidateInfo candidate) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                JSONObject upjson = new JSONObject();
                ServerConnectionManager.MessageHandler offer = new ServerConnectionManager.MessageHandler("trickle");
                serverConnectionManager.messageHandler.add(offer);
                jsonPut(upjson,"mms",offer.getMms());
                jsonPut(upjson,"transaction",offer.getTransaction());
                jsonPut(upjson,"session_id",serverConnectionManager.session_id);
                jsonPut(upjson,"handle_id",serverConnectionManager.handle_id);
                JSONObject json = new JSONObject();
                jsonPut(json, "candidate", candidate.sdp);
                jsonPut(json, "sdpMid", candidate.sdpMid);
                jsonPut(json, "sdpMLineIndex", candidate.sdpMLineIndex);
                jsonPut(json, "candidate", candidate.sdp);
                jsonPut(upjson,"candidate",json);
                Log.d(TAG,"C->S: " + upjson.toString());
                if (initiator) {
                    // Call initiator sends ice candidates to GAE server.
                    if (roomState != ConnectionState.CONNECTED) {
                        reportError("Sending ICE candidate in non connected state.");
                        return;
                    }
                    wsClient.send(upjson.toString());
                    if (connectionParameters.loopback) {
                        events.onRemoteIceCandidate(candidate);
                    }
                } else {
                    // Call receiver sends ice candidates to websocket server.
                    wsClient.send(upjson.toString());
                }
            }
        });
    }

    // Send removed Ice candidates to the other participant.
    @Override
    public void sendLocalIceCandidateRemovals(final IceCandidateInfo[] candidates) {
        Log.d(TAG,"SendLocalIceCandidateRemovals");
        handler.post(new Runnable() {
            @Override
            public void run() {
                JSONObject json = new JSONObject();
                jsonPut(json, "type", "remove-candidates");
                JSONArray jsonArray = new JSONArray();
                for (final IceCandidateInfo candidate : candidates) {
                    jsonArray.put(toJsonCandidate(candidate));
                }
                jsonPut(json, "candidates", jsonArray);
                if (initiator) {
                    // Call initiator sends ice candidates to GAE server.
                    if (roomState != ConnectionState.CONNECTED) {
                        reportError("Sending ICE candidate removals in non connected state.");
                        return;
                    }
                    wsClient.send(json.toString());
                    if (connectionParameters.loopback) {
                        events.onRemoteIceCandidatesRemoved(candidates);
                    }
                } else {
                    // Call receiver sends ice candidates to websocket server.
                    wsClient.send(json.toString());
                }
            }
        });
    }

    // --------------------------------------------------------------------
    // WebSocketChannelEvents interface implementation.
    // All events are called by WebSocketChannelClient on a local looper thread
    // (passed to WebSocket client constructor).
   @Override
    public void onWebSocketMessage(final String msg) {
        if (wsClient.getState() != WebSocketChannelClient.WebSocketConnectionState.REGISTERED) {
            Log.e(TAG, "Got WebSocket message in non registered state.");
            return;
        }
        try{
            Log.d(TAG,"onWebSocketMessage");
            JSONObject json = new JSONObject(msg);
            if(msg.length() > 0){
                String type = json.getString("mms");
                if(!json.has("transaction"))
                {
                    return;
                }
                String receive_transaction = json.getString("transaction");
                int check = CheckIfInServerManager(receive_transaction);
                if(check == 10000){
                    Log.d(TAG,"Do not have sended this message");
                    reportError("Do not have sended this message");
                }
                if(serverConnectionManager.messageHandler.get(check).getMms().equals("create")){
                    Log.d(TAG,"Receive message from create");
                    if(type.equals("success")){
                        JSONObject data = json.getJSONObject("data");
                        Log.d(TAG,"data = " + data.toString());
                        String session_Id = data.getString("id");
                        Log.d(TAG,"session_id = " + session_Id);
                        long test = Long.parseLong(session_Id);
                        serverConnectionManager.session_id = test;
                        ServerConnectionManager.MessageHandler attach_message =
                                new ServerConnectionManager.MessageHandler("attach");
                        String service_name = "service.echo";
                        String opaque_id = "echo-123";
                        serverConnectionManager.messageHandler.add(attach_message);
                        serverConnectionManager.service_name = service_name;
                        serverConnectionManager.opaque_id = opaque_id;
                        wsClient.sendAttach(attach_message,serverConnectionManager.session_id,service_name,opaque_id);
                        serverConnectionManager.messageHandler.remove(check);
                    }else{
                        reportError("After create Server return error");
                    }
                }
                else if(serverConnectionManager.messageHandler.get(check).getMms().equals("attach")){
                    Log.d(TAG,"Receive message from attach");
                    if(type.equals("success")){
                        roomState = ConnectionState.CONNECTED;
                        JSONObject data = json.getJSONObject("data");
                        String handle_Id = data.getString("id");
                        long tmp = Long.parseLong(handle_Id);
                        serverConnectionManager.handle_id = tmp;
                        ServerConnectionManager.MessageHandler local_message =
                                new ServerConnectionManager.MessageHandler("message");
                        serverConnectionManager.messageHandler.add(local_message);
                        wsClient.sendLocalMessage(local_message,serverConnectionManager.session_id,serverConnectionManager.handle_id,
                                true,true);
                        serverConnectionManager.messageHandler.remove(check);
                    }else{
                        reportError("After create Server return error");
                    }
                }
                else if (serverConnectionManager.messageHandler.get(check).getMms().equals("message")){
                    Log.d(TAG,"Receive message from message");
                    if(type.equals("event")){
                        if(json.isNull("jsep"))
                        {
                            Log.d(TAG,"try to connected to room");
                            serverConnectionManager.messageHandler.remove(check);
                            initiator = params.initiator;
                            events.onConnectedToRoom(params);
                        }
                        else{
                            Log.d(TAG,"Try to set sdp answer");
                            JSONObject sdpjson = json.getJSONObject("jsep");
                            //serverConnectionManager.messageHandler.remove(check);
                            SessionDescriptionInfo sdp = new SessionDescriptionInfo("answer",
                                    sdpjson.getString("sdp"));
                            events.onRemoteDescription(sdp);
                        }
                    }
                }
                else {
                    return;
                }
            }
        }catch (JSONException e){
            reportError("onWebSocketError: " + e.getMessage());
        }
    }

    public int CheckIfInServerManager(final String transaction){
        for(int i = 0;i < serverConnectionManager.messageHandler.size();i++){
            if(transaction.equals(serverConnectionManager.messageHandler.get(i).getTransaction())){
                return i;
            }
        }
        return 10000;
    }

    @Override
    public void onWebSocketClose() {
        events.onChannelClose();
    }

    @Override
    public void onWebSocketError(String description) {
        reportError("WebSocket error: " + description);
    }

    // --------------------------------------------------------------------
    // Helper functions.
    private void reportError(final String errorMessage) {
        Log.e(TAG, errorMessage);
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.ERROR) {
                    roomState = ConnectionState.ERROR;
                    events.onChannelError(errorMessage);
                }
            }
        });
    }

    // Put a |key|->|value| mapping in |json|.
    private static void jsonPut(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    // Converts a Java candidate to a JSONObject.
    private JSONObject toJsonCandidate(final IceCandidateInfo candidate) {
        JSONObject json = new JSONObject();
        jsonPut(json, "label", candidate.sdpMLineIndex);
        jsonPut(json, "id", candidate.sdpMid);
        jsonPut(json, "candidate", candidate.sdp);
        return json;
    }

    // Converts a JSON candidate to a Java object.
    IceCandidateInfo toJavaCandidate(JSONObject json) throws JSONException {
        return new IceCandidateInfo(
                json.getString("id"), json.getInt("label"), json.getString("candidate"));
    }
}