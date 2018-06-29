package mediaengine.fritt.mediaengine;

import android.graphics.SurfaceTexture;
import android.view.Surface;

import org.webrtc.EglBase;

/*public class MEglBase extends EglBase {
    public MEglBase() {
        super();
    }

    public EglBase.Context getContext(){
        super.create();
    }

    @Override
    public void createSurface(Surface surface) {
    }

    @Override
    public void createSurface(SurfaceTexture surfaceTexture) {

    }

    @Override
    public void createDummyPbufferSurface() {

    }

    @Override
    public void createPbufferSurface(int i, int i1) {

    }

    @Override
    public Context getEglBaseContext() {
        return null;
    }

    @Override
    public boolean hasSurface() {
        return false;
    }

    @Override
    public int surfaceWidth() {
        return 0;
    }

    @Override
    public int surfaceHeight() {
        return 0;
    }

    @Override
    public void releaseSurface() {

    }

    @Override
    public void release() {

    }

    @Override
    public void makeCurrent() {

    }

    @Override
    public void detachCurrent() {

    }

    @Override
    public void swapBuffers() {

    }
}*/

public class MEglBase{
    private EglBase glBase;

    public MEglBase(){
        glBase = EglBase.create();
    }

    public EglBase.Context getContext(){
        return glBase.getEglBaseContext();
    }

    public void release(){glBase.release();}
}
