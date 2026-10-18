package local.tvgpt.assistant.digests;

import java.io.File;

public interface ImageGenerator {
  Result generate(String operationId, String prompt, File destination);

  final class Result {
    public final String state, reportedModel, reason;
    public final File file;

    public Result(String state, File file, String model, String reason) {
      this.state = state;
      this.file = file;
      this.reportedModel = model;
      this.reason = reason;
    }
  }
}
