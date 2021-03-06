package mediaengine.fritt.mediaengine;

import org.webrtc.PeerConnection;
import org.webrtc.PeerConnection.IceServer;

public class IceServerInfo extends IceServer{

    public IceServerInfo(String uri) {
        super(uri);
    }

    public IceServerInfo(String uri, String username, String password) {
        super(uri, username, password);
    }

    public IceServerInfo(String uri, String username, String password, PeerConnection.TlsCertPolicy tlsCertPolicy) {
        super(uri, username, password, tlsCertPolicy);
    }

    public IceServerInfo(String uri, String username, String password, PeerConnection.TlsCertPolicy tlsCertPolicy, String hostname) {
        super(uri, username, password, tlsCertPolicy, hostname);
    }
}
