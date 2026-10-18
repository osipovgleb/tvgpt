package local.tvgpt.assistant;

import android.content.Intent;
import android.speech.*;

/** System assistant metadata requires a recognizer. Live audio is handled by the voice session. */
public class RecognitionAdapter extends RecognitionService {
  @Override
  protected void onStartListening(Intent intent, Callback callback) {
    try {
      callback.error(SpeechRecognizer.ERROR_CLIENT);
    } catch (android.os.RemoteException ignored) {
    }
  }

  @Override
  protected void onCancel(Callback callback) {}

  @Override
  protected void onStopListening(Callback callback) {}
}
