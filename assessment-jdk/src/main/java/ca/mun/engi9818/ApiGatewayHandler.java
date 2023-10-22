package ca.mun.engi9818;

import java.util.Base64;
import java.util.List;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
// import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;


public class ApiGatewayHandler implements RequestHandler<APIGatewayProxyRequestEvent, ReturnResponseRecord> {

    @Override
    public ReturnResponseRecord handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        MainHandler handler = new MainHandler();
        System.out.println("Received request:");
        System.out.println(input);

        String rawBody = input.getBody();

        ObjectMapper objectMapper = new ObjectMapper();
        RequestBody body;
        try {
            body = objectMapper.readValue(rawBody, RequestBody.class);
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return new ReturnResponseRecord(null, "Error parsing JSON");
        }
        byte[] submission = Base64.getDecoder().decode(body.submission());
        byte[] project = Base64.getDecoder().decode(body.project());
        List<String> files = body.files();

        ReturnResponseRecord resp = null;
        try {
            resp = new ReturnResponseRecord(handler.handleRequest(submission, project, files), null);
        } catch (SubmissionHandlingException e) {
            resp = new ReturnResponseRecord(e.getLog(), e.getMessage());
        }
        // APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        // response.setBody(resp);
        // return response;
        return resp;
    }
    
}

record ReturnResponseRecord(String body, String error) { }

record RequestBody(String submission, String project, List<String> files) { }
