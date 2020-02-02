
package fir.needle.web.server.http.rest;


import fir.needle.joint.io.CharArea;

public interface RestListener {

    void onRequestStarted(CharSequence method, RestResponse response);

    void onParameter(CharSequence name, CharSequence value);

    void onBodyPart(CharArea charArea, long startIndex, long length);

    void onError(Throwable exception);

    void onRequestFinished();
}