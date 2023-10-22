package ca.mun.engi9818;

import java.util.Base64;
import java.util.List;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

public class ManualInvokeHander implements RequestHandler<EventRecord, String> {
    @Override
    public String handleRequest(EventRecord evt, Context ctx) {
        MainHandler handler = new MainHandler();
        try {
            byte[] submission = Base64.getDecoder().decode(evt.submission());
            byte[] project = Base64.getDecoder().decode(evt.project());
            return handler.handleRequest(submission, project, evt.files());
        } catch (SubmissionHandlingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }
}

record EventRecord(String submission, String project, List<String> files) {

}
