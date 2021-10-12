package app.dassana.core.restapi;

import app.dassana.core.contentmanager.RemoteContentDownloadApi;
import app.dassana.core.contentmanager.infra.S3Manager;
import app.dassana.core.launch.model.ProcessingResponse;
import app.dassana.core.launch.model.Request;
import app.dassana.core.workflow.StepRunnerApi;
import app.dassana.core.workflow.infra.LambdaStepRunner;
import app.dassana.core.workflow.model.Step;
import app.dassana.core.workflow.model.StepRunResponse;
import app.dassana.core.workflow.model.Workflow;
import app.dassana.core.workflow.model.WorkflowOutputWithRisk;
import app.dassana.core.workflow.processor.EventBridgeHandler;
import app.dassana.core.workflow.processor.PostProcessor;
import io.micronaut.context.annotation.Replaces;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.inject.Singleton;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import software.amazon.awssdk.services.s3.S3Client;

public class Common {
  @Singleton
  @Replaces(PostProcessor.class)
  public static class FakePostProcessor extends PostProcessor {

    @Override
    public String handlePostProcessor(Request request, WorkflowOutputWithRisk normalizationResult,
        String processedAlertWithS3key) throws Exception {
      return processedAlertWithS3key;
    }
  }


  @Singleton
  @Replaces(EventBridgeHandler.class)
  public static class FakeEventBridgeHandler extends EventBridgeHandler {

    @Override
    public void handleEventBridge(ProcessingResponse processingResponse, String normalizerId) {
    }
  }

  @Singleton
  @Replaces(LambdaStepRunner.class)
  public static class MockLambdaStepRunner implements StepRunnerApi {

    public MockLambdaStepRunner() {
    }


    @Override
    public StepRunResponse runStep(Workflow workflow, Step step, String inputJson,
        String simpleOutputJsonStr) throws Exception {

      StepRunResponse stepRunResponse = new StepRunResponse();
      if (step.getId().contentEquals("resource-info")) {

        //not the best solution but here we simulate the dynamic nature of functions, i.e. based on the input, we
        // generate the response from lambda function

        JSONObject jsonObject = new JSONObject(inputJson);
        JSONObject testHintJsonObj = jsonObject.optJSONObject("test-hint");

        if (testHintJsonObj != null) {
          String resInfo = testHintJsonObj.optString("resource-info");
          if (StringUtils.isNotBlank(resInfo)) {
            stepRunResponse.setResponse(IOUtils.toString(
                Thread.currentThread().getContextClassLoader().getResourceAsStream(resInfo), Charset.defaultCharset()));
          }
        }


      }

      if (step.getId().contentEquals("getTags")) {
        stepRunResponse.setResponse(IOUtils.toString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream("responses/tags.json"),
            Charset.defaultCharset()));
      }

      if (step.getId().contentEquals("list-of-attached-eni")) {
        stepRunResponse.setResponse(IOUtils.toString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream("responses/list-of-attached-eni.json"),
            Charset.defaultCharset()));
      }

      if (step.getId().contentEquals("lookup")) {
        stepRunResponse.setResponse(IOUtils.toString(
            Thread.currentThread().getContextClassLoader().getResourceAsStream("responses/lookup.json"),
            Charset.defaultCharset()));

      }

      return stepRunResponse;
    }

  }


  @Singleton
  @Replaces(RemoteContentDownloadApi.class)
  public static class FakeS3Manager extends S3Manager {

    public FakeS3Manager(S3Client s3Client) {
      super(s3Client);
    }

    @Override
    public String uploadedToS3(Optional<WorkflowOutputWithRisk> normalizationResult, String jsonToUpload) {
      return jsonToUpload;
    }

    @Override
    public List<String> downloadContent() {
      return new LinkedList<>();
    }

    @Override
    public Long getLastUpdated(boolean useCache) throws ExecutionException {
      return 0L;
    }
  }

}