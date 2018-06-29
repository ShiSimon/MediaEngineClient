package mediaengine.fritt.mediaengine;

import org.webrtc.VideoRenderer;

import java.util.ArrayList;
import java.util.List;

public class MediaEngineRenderer{
    public final ProxyRenderer localrenderer = new ProxyRenderer();
    public final ProxyRenderer remoterenderer = new ProxyRenderer();
    public SurfaceViewRender fullscreenRenderer;
    public SurfaceViewRender pipRenderer;

    public final List<VideoRenderer.Callbacks> remoteRenderers =
            new ArrayList<VideoRenderer.Callbacks>();
    public boolean isSwappedFeeds = false;

    public MediaEngineRenderer(SurfaceViewRender fullscreenRenderer,SurfaceViewRender pipRenderer){
        remoteRenderers.add(remoterenderer);
        this.fullscreenRenderer = fullscreenRenderer;
        this.pipRenderer = pipRenderer;
    }


    public void setRenderer(boolean isSwappedFeeds){
        this.isSwappedFeeds = isSwappedFeeds;
        localrenderer.setTarget(isSwappedFeeds ? fullscreenRenderer : pipRenderer);
        remoterenderer.setTarget(isSwappedFeeds ? pipRenderer : fullscreenRenderer);
        fullscreenRenderer.setMirror(isSwappedFeeds);
        pipRenderer.setMirror(!isSwappedFeeds);
    }


    public void release(){
        remoterenderer.setTarget(null);
        localrenderer.setTarget(null);
    }
}

/*public class MediaEngineRenderer extends VideoRenderer{

    public final ProxyRenderer localrenderer = new ProxyRenderer();
    public final ProxyRenderer remoterenderer = new ProxyRenderer();

    public final List<VideoRenderer.Callbacks> remoteRenderers =
            new ArrayList<VideoRenderer.Callbacks>();
    public interface myCallbacks extends VideoRenderer.Callbacks{

    }

    public void init(){
        remoteRenderers.add(remoterenderer);
    }

    public MediaEngineRenderer(Callbacks callbacks) {
        super(callbacks);
    }

    public void release(){
        remoterenderer.setTarget(null);
        localrenderer.setTarget(null);
    }
}*/


